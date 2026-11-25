package com.carmotors.servicio;

import com.carmotors.dao.OrdenServicioDAO;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.Vehiculo;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransicionesOrdenServiceTest {
    @Test void cicloCompletoConsumeYFacturaUnaSolaVezYNotificaTrasCommit() {
        Escenario e = new Escenario("Pendiente");
        e.servicio.iniciarOrden(1);
        e.servicio.completarOrden(1);
        assertEquals("Completado", e.dao.orden.getEstado());
        assertEquals(e.fecha, e.dao.orden.getFechaCompletado());
        assertEquals(9, e.dao.stock);
        assertEquals(List.of(7), e.eventos);
        assertThrows(ValidacionException.class, () -> e.servicio.completarOrden(1));
        assertEquals(9, e.dao.stock);
        e.servicio.facturarOrden(1);
        assertTrue(e.dao.factura);
        assertThrows(ValidacionException.class, () -> e.servicio.facturarOrden(1));
        e.servicio.entregarOrden(1);
        assertEquals("Entregado", e.dao.orden.getEstado());
        assertEquals(e.fecha, e.dao.orden.getFechaEntrega());
        assertEquals(1, e.consumos);
        assertEquals(1, e.facturaciones);
        assertEquals(4, e.commits);
    }

    @Test void rechazaTodosLosSaltosRetrocesosYRepeticiones() {
        List<String> estados = List.of("Pendiente", "En Proceso", "Completado", "Facturado", "Entregado");
        Map<String,String> siguiente = Map.of("Pendiente","En Proceso","En Proceso","Completado","Completado","Facturado","Facturado","Entregado");
        for (String actual : estados) for (String destino : estados) {
            if (destino.equals(siguiente.get(actual))) continue;
            Escenario e = new Escenario(actual);
            assertThrows(ValidacionException.class, () -> e.servicio.cambiarEstado(1, destino), actual+" a "+destino);
            assertEquals(actual, e.dao.orden.getEstado());
            assertEquals(0,e.commits); assertEquals(0,e.consumos); assertEquals(0,e.facturaciones);
        }
    }

    @Test void sinIntegracionesNoPermiteCompletarNiFacturar() {
        Escenario e = new Escenario("En Proceso");
        OrdenServicioService incompleto = new OrdenServicioService(e.dao, e.vehiculos, e::conexion, e.reloj);
        assertThrows(ValidacionException.class, () -> incompleto.completarOrden(1));
        e.dao.orden.setEstado("Completado");
        assertThrows(ValidacionException.class, () -> incompleto.facturarOrden(1));
        assertEquals(0,e.commits); assertTrue(e.eventos.isEmpty());
    }

    @Test void consumoIncompletoNoCambiaStockNiEstadoNiPublica() {
        Escenario e = new Escenario("En Proceso"); e.consumoValido=false;
        assertThrows(ValidacionException.class, () -> e.servicio.completarOrden(1));
        assertEquals("En Proceso",e.dao.orden.getEstado());
        assertEquals(10,e.dao.stock); assertNull(e.dao.orden.getFechaCompletado());
        assertTrue(e.eventos.isEmpty()); assertEquals(1,e.rollbacks);
    }

    @Test void facturacionSinPersistenciaODuplicadaEsRechazada() {
        Escenario e = new Escenario("Completado"); e.crearFactura=false;
        assertThrows(ValidacionException.class, () -> e.servicio.facturarOrden(1));
        assertEquals("Completado",e.dao.orden.getEstado()); assertFalse(e.dao.factura);
        e.dao.factura=true;
        assertThrows(ValidacionException.class, () -> e.servicio.facturarOrden(1));
        assertEquals(1,e.facturaciones); // No vuelve a invocar el contrato cuando ya existe.
    }

    @Test void errorDeEstadoYCommitReviertenTodosLosEfectos() {
        for (boolean falloCommit : List.of(false,true)) {
            Escenario e=new Escenario("En Proceso");e.dao.fallarActualizacion=!falloCommit;e.fallarCommit=falloCommit;
            assertThrows(RuntimeException.class, () -> e.servicio.completarOrden(1));
            assertEquals(10,e.dao.stock);assertFalse(e.dao.consumido);
            assertEquals("En Proceso",e.dao.orden.getEstado());assertNull(e.dao.orden.getFechaCompletado());
            assertTrue(e.eventos.isEmpty());assertEquals(1,e.rollbacks);assertEquals(1,e.cierres);
        }
        Escenario e=new Escenario("Completado");e.dao.fallarActualizacion=true;
        assertThrows(ValidacionException.class, () -> e.servicio.facturarOrden(1));
        assertFalse(e.dao.factura);assertEquals("Completado",e.dao.orden.getEstado());
    }

    @Test void entregaRequiereFacturaYFechasCoherentes() {
        Escenario sinFactura=new Escenario("Facturado");
        assertThrows(ValidacionException.class, () -> sinFactura.servicio.entregarOrden(1));
        Escenario fechaInvalida=new Escenario("En Proceso");fechaInvalida.dao.orden.setFechaRecepcion(fechaInvalida.fecha.plusDays(1));
        assertThrows(ValidacionException.class, () -> fechaInvalida.servicio.completarOrden(1));
        assertEquals(0,fechaInvalida.consumos);
    }

    @Test void ordenInexistenteEntradaInvalidaYObservadorFallido() {
        Escenario e=new Escenario("Pendiente");
        assertThrows(ValidacionException.class, () -> e.servicio.cambiarEstado(0,"Completado"));
        assertThrows(ValidacionException.class, () -> e.servicio.cambiarEstado(1,"Otro"));
        assertThrows(ValidacionException.class, () -> e.servicio.iniciarOrden(99));
        e.publicador.suscribir(id -> {throw new IllegalStateException("Fallo de observador");});
        e.servicio.iniciarOrden(1); e.servicio.completarOrden(1);
        assertEquals("Completado",e.dao.orden.getEstado());assertEquals(2,e.commits);
        assertEquals(List.of(7),e.eventos);
    }

    private static class Escenario {
        final Memoria dao=new Memoria();
        final LocalDateTime fecha=LocalDateTime.of(2026,11,17,10,0);
        final Clock reloj=Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"),ZoneOffset.UTC);
        final PublicadorInventario publicador=new PublicadorInventario();
        final List<Integer> eventos=new ArrayList<>();
        int consumos,facturaciones,commits,rollbacks,cierres;
        boolean consumoValido=true,crearFactura=true,fallarCommit;
        Connection actual;
        final VehiculoConsulta vehiculos=new VehiculoConsulta(){
            public Optional<Vehiculo> buscarPorId(int id){return Optional.empty();}
            public Optional<Vehiculo> buscarPorPlaca(String placa){return Optional.empty();}
        };
        final OrdenServicioService servicio;
        Escenario(String estado){
            dao.orden.setId(1);dao.orden.setEstado(estado);dao.orden.setFechaRecepcion(fecha.minusDays(1));
            publicador.suscribir(id->{assertTrue(commits>0);assertTrue(cierres>0);eventos.add(id);});
            servicio=new OrdenServicioService(dao,vehiculos,this::conexion,reloj,
                    (con,id)->{assertSame(actual,con);consumos++;dao.stock--;dao.consumido=consumoValido;return Set.of(7);},
                    (con,id,emision)->{assertSame(actual,con);assertEquals(fecha,emision);facturaciones++;dao.factura=crearFactura;},publicador);
        }
        Connection conexion(){
            String estado=dao.orden.getEstado();LocalDateTime completado=dao.orden.getFechaCompletado(),entrega=dao.orden.getFechaEntrega();
            int stock=dao.stock;boolean consumido=dao.consumido,factura=dao.factura;
            actual=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                switch(m.getName()){
                    case "setAutoCommit":assertEquals(false,a[0]);return null;
                    case "commit":if(fallarCommit)throw new SQLException("Fallo de commit");commits++;return null;
                    case "rollback":rollbacks++;dao.stock=stock;dao.consumido=consumido;dao.factura=factura;
                        dao.orden.setEstado(estado);dao.orden.setFechaCompletado(completado);dao.orden.setFechaEntrega(entrega);return null;
                    case "close":cierres++;return null;
                    default:throw new AssertionError(m.getName());
                }
            });return actual;
        }
    }
    private static class Memoria implements OrdenServicioDAO {
        final OrdenServicio orden=new OrdenServicio();int stock=10;boolean consumido,factura,fallarActualizacion;
        public Optional<OrdenServicio> bloquearPorId(Connection c,int id){return id==1?Optional.of(orden):Optional.empty();}
        public boolean actualizarEstado(Connection c,int id,String actual,String nuevo,LocalDateTime fecha){
            if(fallarActualizacion)return false;
            if(!orden.getEstado().equals(actual))return false;
            orden.setEstado(nuevo);
            if(nuevo.equals("Completado"))orden.setFechaCompletado(fecha);
            if(nuevo.equals("Entregado"))orden.setFechaEntrega(fecha);
            return true;
        }
        public boolean existeFactura(Connection c,int id){return factura;}
        public boolean consumoCompleto(Connection c,int id){return consumido;}
        public int crear(Connection c,OrdenServicio o){throw new UnsupportedOperationException();}
        public Optional<OrdenServicio> buscarPorId(Connection c,int id){return bloquearPorId(c,id);}
        public List<OrdenServicio> listar(Connection c){return List.of(orden);}
        public List<OrdenServicio> listarPorVehiculo(Connection c,int id){return List.of();}
        public List<OrdenServicio> listarPorEstado(Connection c,String estado){return List.of();}
    }
}
