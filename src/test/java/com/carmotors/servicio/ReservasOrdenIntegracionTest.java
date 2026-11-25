package com.carmotors.servicio;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.InventarioReservasJdbc;
import com.carmotors.dao.OrdenServicioDAOJdbc;
import com.carmotors.excepcion.ConexionException;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.Vehiculo;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Fixtures propios y pruebas de concurrencia reales en MySQL, sin recrear la base. */
class ReservasOrdenIntegracionTest {
    private final Clock reloj=Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"),ZoneOffset.UTC);
    private final OrdenServicioDAOJdbc ordenes=new OrdenServicioDAOJdbc();
    private final PublicadorInventario publicador=new PublicadorInventario();
    private final List<Integer> eventos=new CopyOnWriteArrayList<>();
    private int repuesto,otroRepuesto,temprano,tardio,orden,otraOrden;
    private ReservasOrdenService reservas;

    @BeforeEach void preparar() throws Exception {
        try(Connection con=abrir()) { assumeTrue(con.isValid(2),"MySQL no disponible"); }
        catch(ConexionException e) { assumeTrue(false,"MySQL no disponible"); }
        publicador.suscribir(eventos::add);
        String codigo="RS"+Long.toUnsignedString(System.nanoTime(),36);
        try(Connection con=abrir()) {
            repuesto=insertar(con,"INSERT INTO repuestos(nombre,repuesto_tipo_id,marca_id,precio_venta,stock_minimo) VALUES (?,1,1,100.00,1)",codigo);
            otroRepuesto=insertar(con,"INSERT INTO repuestos(nombre,repuesto_tipo_id,marca_id,precio_venta,stock_minimo) VALUES (?,1,1,200.00,1)",codigo+"B");
            temprano=insertar(con,"INSERT INTO lotes(codigo,proveedor_id,fecha_ingreso) VALUES (?,1,'2026-11-01')",codigo+"A");
            tardio=insertar(con,"INSERT INTO lotes(codigo,proveedor_id,fecha_ingreso) VALUES (?,1,'2026-11-02')",codigo+"B");
            ejecutar(con,"INSERT INTO lotes_repuestos(lote_id,repuesto_id,cantidad_ingresada,cantidad_actual,costo_unitario,fecha_caducidad) VALUES (?,?,4,4,50.00,'2026-11-19')",temprano,repuesto);
            ejecutar(con,"INSERT INTO lotes_repuestos(lote_id,repuesto_id,cantidad_ingresada,cantidad_actual,costo_unitario,fecha_caducidad) VALUES (?,?,6,6,50.00,'2026-11-30')",tardio,repuesto);
            ejecutar(con,"INSERT INTO lotes_repuestos(lote_id,repuesto_id,cantidad_ingresada,cantidad_actual,costo_unitario) VALUES (?,?,5,5,80.00)",tardio,otroRepuesto);
            OrdenServicio o=new OrdenServicio();o.setVehiculoId(1);o.setTipoMantenimiento("Preventivo");o.setEstado("En Proceso");
            o.setFechaRecepcion(LocalDateTime.of(2026,11,17,9,0));o.setDescripcionProblema("Fixture reservas");
            orden=ordenes.crear(con,o);otraOrden=ordenes.crear(con,o);
        }
        reservas=crear(new InventarioReservasJdbc());
    }

    private Connection abrir() { return ConexionFactory.getInstancia().crearConexion(); }
    private ReservasOrdenService crear(InventarioReservas inventario) {
        return new ReservasOrdenService(ordenes,inventario,this::abrir,reloj,publicador);
    }
    private OrdenServicioService transiciones(OrdenServicioDAOJdbc dao,ReservasOrdenService consumo) {
        VehiculoConsulta vehiculos=new VehiculoConsulta() {
            public Optional<Vehiculo> buscarPorId(int id){return Optional.empty();}
            public Optional<Vehiculo> buscarPorPlaca(String placa){return Optional.empty();}
        };
        return new OrdenServicioService(dao,vehiculos,this::abrir,reloj,consumo,
                (con,id,fecha)->{throw new UnsupportedOperationException("Facturación fuera de esta tarea");},publicador);
    }

    @Test void reservaFefoConservaPrecioYCompletaUnaSolaVez() throws Exception {
        reservas.reservar(orden,repuesto,6);
        assertEquals(10,fisico(repuesto));assertEquals(6,activas(orden,repuesto));
        assertEquals(4,numero("SELECT SUM(cantidad) FROM reservas_repuestos WHERE servicio_id=? AND lote_id=?",orden,temprano));
        assertEquals(2,numero("SELECT SUM(cantidad) FROM reservas_repuestos WHERE servicio_id=? AND lote_id=?",orden,tardio));
        try(Connection con=abrir()){ejecutar(con,"UPDATE repuestos SET precio_venta=999.00 WHERE id=?",repuesto);}
        eventos.clear();
        OrdenServicioService servicio=transiciones(ordenes,reservas);
        servicio.completarOrden(orden);
        assertEquals(4,fisico(repuesto));assertEquals(0,activas(orden,repuesto));
        assertEquals("Completado",estado(orden));assertEquals(List.of(repuesto),eventos);
        assertEquals(100,numero("SELECT precio_unitario_venta FROM servicios_repuestos WHERE servicio_id=? AND repuesto_id=?",orden,repuesto));
        assertThrows(ValidacionException.class,()->servicio.completarOrden(orden));assertEquals(4,fisico(repuesto));
    }

    @Test void ajustarYFalloDeStockConservanHistorialYReservasPrevias() throws Exception {
        reservas.reservar(orden,repuesto,6);
        try(Connection con=abrir()){ejecutar(con,"UPDATE repuestos SET precio_venta=999.00 WHERE id=?",repuesto);}
        reservas.ajustar(orden,repuesto,8);
        assertEquals(8,activas(orden,repuesto));assertEquals(10,fisico(repuesto));
        assertEquals(6,numero("SELECT SUM(r.cantidad) FROM reservas_repuestos r JOIN reserva_estados e ON e.id=r.estado_id WHERE r.servicio_id=? AND r.repuesto_id=? AND e.nombre='LIBERADA'",orden,repuesto));
        eventos.clear();
        assertThrows(ValidacionException.class,()->reservas.ajustar(orden,repuesto,11));
        assertEquals(8,activas(orden,repuesto));assertEquals(10,fisico(repuesto));assertTrue(eventos.isEmpty());
        assertEquals(100,numero("SELECT precio_unitario_venta FROM servicios_repuestos WHERE servicio_id=? AND repuesto_id=?",orden,repuesto));
    }

    @Test void liberarNoSumaStockYNoPermiteCompletarLineaSinCobertura() throws Exception {
        reservas.reservar(orden,repuesto,6);reservas.liberar(orden,repuesto);
        assertEquals(10,fisico(repuesto));assertEquals(0,activas(orden,repuesto));
        assertThrows(ValidacionException.class,()->transiciones(ordenes,reservas).completarOrden(orden));
        assertEquals("En Proceso",estado(orden));
        reservas.ajustar(orden,repuesto,6);assertEquals(6,activas(orden,repuesto));
    }

    @Test void loteVencidoDebeReasignarseAntesDeConsumir() throws Exception {
        reservas.reservar(orden,repuesto,3);
        try(Connection con=abrir()){ejecutar(con,"UPDATE lotes_repuestos SET fecha_caducidad='2026-11-16' WHERE lote_id=? AND repuesto_id=?",temprano,repuesto);}
        assertThrows(ValidacionException.class,()->transiciones(ordenes,reservas).completarOrden(orden));
        assertEquals(10,fisico(repuesto));assertEquals(3,activas(orden,repuesto));
        reservas.ajustar(orden,repuesto,3);transiciones(ordenes,reservas).completarOrden(orden);
        assertEquals(7,fisico(repuesto));
    }

    @Test void falloAlCambiarEstadoRevierteStockReservasYFechas() throws Exception {
        reservas.reservar(orden,repuesto,3);reservas.reservar(orden,otroRepuesto,2);eventos.clear();
        OrdenServicioDAOJdbc falla=new OrdenServicioDAOJdbc(){
            @Override public boolean actualizarEstado(Connection con,int id,String actual,String nuevo,LocalDateTime fecha) {
                throw new DaoException("Fallo inyectado después del consumo");
            }
        };
        assertThrows(DaoException.class,()->transiciones(falla,reservas).completarOrden(orden));
        assertEquals(10,fisico(repuesto));assertEquals(5,fisico(otroRepuesto));
        assertEquals(3,activas(orden,repuesto));assertEquals(2,activas(orden,otroRepuesto));
        assertEquals("En Proceso",estado(orden));assertTrue(eventos.isEmpty());
        try(Connection con=abrir()){assertNull(ordenes.buscarPorId(con,orden).orElseThrow().getFechaCompletado());}
    }

    @Test void falloIntermedioDeConsumoRevierteTambienElPrimerLote() throws Exception {
        reservas.reservar(orden,repuesto,6);eventos.clear();
        InventarioReservasJdbc falla=new InventarioReservasJdbc(){
            int llamadas;
            @Override public boolean consumirReserva(Connection con,int id,LocalDateTime fecha) {
                if(++llamadas==2)throw new DaoException("Fallo en el segundo lote");
                return super.consumirReserva(con,id,fecha);
            }
        };
        assertThrows(DaoException.class,()->transiciones(ordenes,crear(falla)).completarOrden(orden));
        assertEquals(10,fisico(repuesto));assertEquals(6,activas(orden,repuesto));assertTrue(eventos.isEmpty());
    }

    @Test void dosOrdenesConcurrentesNoReservanMasDelDisponible() throws Exception {
        List<String> resultados=simultaneas(()->reservas.reservar(orden,repuesto,7),()->reservas.reservar(otraOrden,repuesto,7));
        assertEquals(1,resultados.stream().filter("OK"::equals).count());
        assertEquals(7,activas(orden,repuesto)+activas(otraOrden,repuesto));assertEquals(10,fisico(repuesto));
    }

    @Test void dosFinalizacionesConcurrentesNoDuplicanConsumo() throws Exception {
        reservas.reservar(orden,repuesto,7);eventos.clear();
        OrdenServicioService servicio=transiciones(ordenes,reservas);
        List<String> resultados=simultaneas(()->servicio.completarOrden(orden),()->servicio.completarOrden(orden));
        assertEquals(1,resultados.stream().filter("OK"::equals).count());
        assertEquals(3,fisico(repuesto));assertEquals("Completado",estado(orden));assertEquals(List.of(repuesto),eventos);
    }

    @Test void rechazaCantidadesRepeticionesOrdenCerradaYConsumoSinTransaccion() throws Exception {
        assertThrows(ValidacionException.class,()->reservas.reservar(orden,repuesto,0));
        assertThrows(ValidacionException.class,()->reservas.reservar(orden,Integer.MAX_VALUE,1));
        reservas.reservar(orden,repuesto,2);
        assertThrows(ValidacionException.class,()->reservas.reservar(orden,repuesto,2));
        transiciones(ordenes,reservas).completarOrden(orden);
        assertThrows(ValidacionException.class,()->reservas.ajustar(orden,repuesto,1));
        assertThrows(ValidacionException.class,()->reservas.liberar(orden,repuesto));
        try(Connection con=abrir()){assertThrows(ValidacionException.class,()->reservas.consumir(con,orden));}
    }

    private List<String> simultaneas(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch inicio=new CountDownLatch(1);
        try {
            Future<String> uno=pool.submit(()->intentar(inicio,a));Future<String> dos=pool.submit(()->intentar(inicio,b));
            inicio.countDown();return List.of(uno.get(20,TimeUnit.SECONDS),dos.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();assertTrue(pool.awaitTermination(5,TimeUnit.SECONDS));}
    }
    private String intentar(CountDownLatch inicio,Runnable accion) throws Exception {
        inicio.await();try{accion.run();return "OK";}catch(ValidacionException e){return "RECHAZADA";}
    }
    private long fisico(int repuesto) throws Exception {return numero("SELECT SUM(cantidad_actual) FROM lotes_repuestos WHERE repuesto_id=?",repuesto);}
    private long activas(int orden,int repuesto) throws Exception {
        return numero("SELECT COALESCE(SUM(r.cantidad),0) FROM reservas_repuestos r JOIN reserva_estados e ON e.id=r.estado_id WHERE r.servicio_id=? AND r.repuesto_id=? AND e.nombre='ACTIVA'",orden,repuesto);
    }
    private String estado(int id) throws Exception {try(Connection con=abrir()){return ordenes.buscarPorId(con,id).orElseThrow().getEstado();}}
    private long numero(String sql,Object... args) throws Exception {
        try(Connection con=abrir();PreparedStatement ps=con.prepareStatement(sql)) {
            parametros(ps,args);try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());return rs.getLong(1);}
        }
    }
    private int insertar(Connection con,String sql,Object...args) throws Exception {
        try(PreparedStatement ps=con.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)) {
            parametros(ps,args);ps.executeUpdate();try(ResultSet rs=ps.getGeneratedKeys()){assertTrue(rs.next());return rs.getInt(1);}
        }
    }
    private void ejecutar(Connection con,String sql,Object...args) throws Exception {
        try(PreparedStatement ps=con.prepareStatement(sql)){parametros(ps,args);ps.executeUpdate();}
    }
    private void parametros(PreparedStatement ps,Object...args) throws Exception {for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);}
    @AfterEach void limpiar() throws Exception {
        if(repuesto==0)return;
        try(Connection con=abrir()) {
            ejecutar(con,"DELETE FROM reservas_repuestos WHERE servicio_id IN (?,?)",orden,otraOrden);
            ejecutar(con,"DELETE FROM servicios_repuestos WHERE servicio_id IN (?,?)",orden,otraOrden);
            ejecutar(con,"DELETE FROM servicios WHERE id IN (?,?)",orden,otraOrden);
            ejecutar(con,"DELETE FROM lotes_repuestos WHERE lote_id IN (?,?)",temprano,tardio);
            ejecutar(con,"DELETE FROM lotes WHERE id IN (?,?)",temprano,tardio);
            ejecutar(con,"DELETE FROM repuestos WHERE id IN (?,?)",repuesto,otroRepuesto);
        }
    }
}
