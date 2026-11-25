package com.carmotors.integracion;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.factory.*;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.*;
import com.carmotors.servicio.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Recorrido de integración EJECUTABLE EN TEST; no es la interfaz final del taller. */
public final class RecorridoIntegrado {
    public record Resultado(List<String> pasos,String estadoFinal,long stockInicial,long stockFinal,
                            BigDecimal totalFactura,boolean rollbackFacturaVerificado) { }

    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !"--ejecutar".equals(args[0])) {
            System.out.println("Recorrido de integración CarMotors (Java 17). No es el menú final.");
            System.out.println("Cliente → vehículo → técnico → orden → reserva → completar → factura real → entrega.");
            System.out.println("Usa --ejecutar para probar MySQL local preparado con schema.sql/data.sql.");
            System.out.println("Crea y elimina solo sus fixtures. Facturación con tasa de fixture de 19 %.");
            return;
        }
        try {
            Resultado resultado=ejecutar();
            resultado.pasos().forEach(System.out::println);
            System.out.println("RESULTADO: "+resultado.estadoFinal()+" | stock "+resultado.stockInicial()+" → "+resultado.stockFinal()
                    +" | factura de prueba "+resultado.totalFactura().toPlainString());
        } finally {
            // Solo el ejecutable de pruebas: terminar el hilo auxiliar del driver al salir.
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        }
    }

    public static Resultado ejecutar() throws Exception {
        Supplier<Connection> conexiones=()->ConexionFactory.getInstancia().crearConexion();
        Clock reloj=Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"),ZoneOffset.UTC);
        String sufijo=Long.toUnsignedString(System.nanoTime(),36);
        String placa="I"+sufijo.substring(Math.max(0,sufijo.length()-9));
        int clienteId=0,vehiculoId=0,tecnicoId=0,repuestoId=0,loteId=0,ordenId=0;
        List<String> pasos=new ArrayList<>();
        try {
            ClienteService clientes=new ClienteService(new ClienteDAOFactory().obtenerDAO(),new ClienteVehiculosDAOFactory().obtenerDAO());
            Cliente cliente=clientes.registrar(new Cliente("Prueba integrada", "CI"+sufijo,"3001234567","ci"+sufijo+"@example.com"));
            clienteId=cliente.getId();pasos.add("OK alta de cliente mediante servicio/DAO real");
            VehiculoService vehiculos=new VehiculoService(new VehiculoDAOFactory().obtenerDAO(),new ClienteDAOFactory().obtenerDAO());
            Vehiculo vehiculo=vehiculos.registrar(new Vehiculo(placa,clienteId,1));
            vehiculoId=vehiculo.getId();pasos.add("OK vehículo asociado al cliente nuevo");
            TecnicoService tecnicos=new TecnicoService(new TecnicoDAOFactory(),conexiones);
            Tecnico tecnico=new Tecnico();tecnico.setNombre("Técnico integrado");tecnico.setDocumentoIdentidad("TI"+sufijo);
            tecnicoId=tecnicos.registrar(tecnico);
            PublicadorInventario publicador=new PublicadorInventario();
            // Proveedor y catálogos pertenecen a los fixtures data.sql, no a un módulo nuevo.
            ProveedorConsulta proveedorDeFixture=id->id==1;
            RepuestoService repuestos=new RepuestoService(new RepuestoDAOFactory(),proveedorDeFixture,conexiones,publicador);
            Repuesto r=new Repuesto();r.setNombre("Repuesto integrado "+sufijo);r.setTipoId(1);r.setMarcaId(1);r.setProveedorId(1);
            r.setPrecioCompra(new BigDecimal("50.00"));r.setPrecioVenta(new BigDecimal("100.00"));r.setStockMinimo(1);
            repuestoId=repuestos.crear(r);
            Lote lote=new Lote();lote.setCodigo("CI"+sufijo);lote.setProveedorId(1);lote.setFechaIngreso(LocalDate.of(2026,11,17));
            LoteLinea linea=new LoteLinea();linea.setRepuestoId(repuestoId);linea.setCantidadIngresada(10);linea.setCostoUnitario(new BigDecimal("50.00"));
            lote.agregarLinea(linea);
            loteId=new LoteService(new LoteDAOFactory().obtenerDAO(),proveedorDeFixture,conexiones,()->LocalDate.now(reloj),publicador).registrarIngreso(lote);
            var daoOrdenes=new OrdenServicioDAOFactory().obtenerDAO();
            ReservasOrdenService reservas=new ReservasOrdenService(daoOrdenes,new InventarioReservasFactory().obtenerDAO(),conexiones,reloj,publicador);
            FacturacionOrden factura = new FacturacionService(new BigDecimal("0.19"));
            OrdenServicioService ordenes=new OrdenServicioService(daoOrdenes,new VehiculoConsultaImpl(new VehiculoDAOFactory().obtenerDAO()),conexiones,reloj,reservas,factura,publicador);
            OrdenServicio orden=ordenes.crearOrden(vehiculoId,"Preventivo","Recorrido integrado");
            ordenId=orden.getId();int idOrden=ordenId;
            exigir(orden.getClienteId()==clienteId && orden.getEstado().equals("Pendiente"),"Alta de orden inválida");
            pasos.add("OK orden Pendiente con vehículo y cliente correctos");
            AsignacionTecnicoService asignaciones=new AsignacionTecnicoService(new ServicioTecnicoDAOFactory(),conexiones);
            asignaciones.asignar(ordenId,tecnicoId);asignaciones.registrarTiempo(ordenId,tecnicoId,"02:30");
            exigir(asignaciones.listarPorOrden(ordenId).size()==1,"Falta el responsable de la orden");
            exigir(asignaciones.listarPorOrden(ordenId).get(0).estadoOrden().equals("En Proceso"),"Asignar debe iniciar la orden");
            pasos.add("OK técnico asignado, orden En Proceso y 02:30 horas registradas");
            // Fixture de mano de obra: registrar operaciones del catálogo todavía no tiene servicio propio.
            try(Connection con=conexiones.get()) {sql(con,"INSERT INTO servicios_detalle(servicio_id,servicio_catalogo_id,precio_cobrado) VALUES(?,1,12000.00)",ordenId);}
            reservas.reservar(ordenId,repuestoId,4);
            ExistenciasService stock=new ExistenciasService(new ExistenciasDAOFactory().obtenerDAO(),conexiones,()->LocalDate.now(reloj));
            StockRepuesto reservado=stock.resumen(repuestoId);
            exigir(reservado.fisico()==10 && reservado.reservadoActivo()==4 && reservado.disponible()==6,"La reserva alteró el stock físico");
            pasos.add("OK reserva: físico 10, reservado 4, disponible 6");
            ordenes.completarOrden(ordenId);
            StockRepuesto consumido=stock.resumen(repuestoId);
            exigir(consumido.fisico()==6 && consumido.reservadoActivo()==0,"Consumo inconsistente");
            esperarValidacion(()->ordenes.completarOrden(idOrden));
            exigir(stock.resumen(repuestoId).fisico()==6,"La finalización repetida descontó stock");
            pasos.add("OK completar consume una vez; repetición rechazada");
            // Forzar el fallo después de insertar y actualizar, justo antes del commit.
            var ordenesConFallo = new OrdenServicioService(daoOrdenes,
                    new VehiculoConsultaImpl(new VehiculoDAOFactory().obtenerDAO()),
                    () -> fallarAlConfirmar(conexiones.get()), reloj, reservas, factura, publicador);
            try {
                ordenesConFallo.facturarOrden(ordenId);
                throw new AssertionError("Debía fallar el commit de facturación");
            } catch (DaoException esperado) {
                exigir(esperado.getCause() instanceof SQLException causa
                        && "Fallo de commit del fixture".equals(causa.getMessage()), "Falló antes del commit");
            }
            try(Connection con=conexiones.get()) {
                exigir(!daoOrdenes.existeFactura(con,ordenId),"La factura fallida quedó guardada");
                exigir(daoOrdenes.buscarPorId(con,ordenId).orElseThrow().getEstado().equals("Completado"),"Cambió estado tras fallo de factura");
            }
            exigir(stock.resumen(repuestoId).fisico()==6,"Facturación fallida alteró stock");
            pasos.add("OK rollback de factura: cero facturas, orden Completado, stock intacto");
            // Cambiar el precio vigente para comprobar que la factura conserva el precio de la reserva.
            try (Connection con = conexiones.get()) {
                sql(con, "UPDATE repuestos SET precio_venta=999.00 WHERE id=?", repuestoId);
            }
            facturarEnParalelo(ordenes, ordenId);
            exigir(ordenes.buscarPorId(ordenId).orElseThrow().getEstado().equals("Facturado"),
                    "La factura y el nuevo estado no se confirmaron juntos");
            esperarValidacion(()->ordenes.facturarOrden(idOrden));
            ordenes.entregarOrden(ordenId);
            OrdenServicio finalizada=ordenes.buscarPorId(ordenId).orElseThrow();
            exigir(finalizada.getEstado().equals("Entregado") && finalizada.getFechaEntrega()!=null,"No se entregó la orden");
            BigDecimal total;
            try(Connection con=conexiones.get();PreparedStatement ps=con.prepareStatement("SELECT total_final FROM facturas WHERE servicio_id=?")) {
                ps.setInt(1,ordenId);try(ResultSet rs=ps.executeQuery()) {exigir(rs.next(),"Falta factura de prueba");total=rs.getBigDecimal(1);exigir(!rs.next(),"Factura duplicada");}
            }
            exigir(total.compareTo(new BigDecimal("14756.00"))==0,"Total de prueba inesperado");
            pasos.add("OK factura única 14756.00 con concurrencia, sin descuento adicional");
            pasos.add("OK entrega con fecha y estado persistidos");
            return new Resultado(List.copyOf(pasos),finalizada.getEstado(),10,stock.resumen(repuestoId).fisico(),total,true);
        } finally {
            // Solo IDs creados por esta ejecución, en orden de dependencias.
            try(Connection con=conexiones.get()) {
                con.setAutoCommit(false);
                try {
                    if(ordenId>0) {
                        sql(con,"DELETE FROM facturas WHERE servicio_id=?",ordenId);
                        sql(con,"DELETE FROM reservas_repuestos WHERE servicio_id=?",ordenId);
                        sql(con,"DELETE FROM servicios_repuestos WHERE servicio_id=?",ordenId);
                        sql(con,"DELETE FROM servicios_tecnicos WHERE servicio_id=?",ordenId);
                        sql(con,"DELETE FROM servicios_detalle WHERE servicio_id=?",ordenId);
                        sql(con,"DELETE FROM servicios WHERE id=?",ordenId);
                    }
                    if(loteId>0) {sql(con,"DELETE FROM lotes_repuestos WHERE lote_id=?",loteId);sql(con,"DELETE FROM lotes WHERE id=?",loteId);}
                    if(repuestoId>0) {sql(con,"DELETE FROM proveedores_repuestos WHERE repuesto_id=?",repuestoId);sql(con,"DELETE FROM repuestos WHERE id=?",repuestoId);}
                    if(tecnicoId>0)sql(con,"DELETE FROM tecnicos WHERE id=?",tecnicoId);
                    if(vehiculoId>0)sql(con,"DELETE FROM vehiculos WHERE id=?",vehiculoId);
                    if(clienteId>0)sql(con,"DELETE FROM clientes WHERE id=?",clienteId);
                    con.commit();
                } catch(Exception e) {con.rollback();throw e;}
            }
        }
    }
    private static Connection fallarAlConfirmar(Connection conexion) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, metodo, argumentos) -> {
                    if ("commit".equals(metodo.getName())) {
                        throw new SQLException("Fallo de commit del fixture");
                    }
                    try {
                        return metodo.invoke(conexion, argumentos);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void facturarEnParalelo(OrdenServicioService ordenes, int ordenId) throws Exception {
        var ejecutor = Executors.newFixedThreadPool(2);
        var inicio = new CyclicBarrier(2);
        java.util.concurrent.Callable<Boolean> intento = () -> {
            inicio.await(5, TimeUnit.SECONDS);
            try {
                ordenes.facturarOrden(ordenId);
                return true;
            } catch (ValidacionException e) {
                exigir(e.getMessage().contains("Facturado"), "Rechazo de facturación inesperado");
                return false;
            }
        };
        try {
            var primero = ejecutor.submit(intento);
            var segundo = ejecutor.submit(intento);
            exigir(primero.get(15, TimeUnit.SECONDS) != segundo.get(15, TimeUnit.SECONDS),
                    "Exactamente un intento de facturación debe confirmarse");
        } finally {
            ejecutor.shutdownNow();
        }
    }

    private static void exigir(boolean condicion,String mensaje){if(!condicion)throw new AssertionError(mensaje);}
    private static void esperarValidacion(Runnable accion) {
        try{accion.run();throw new AssertionError("La operación repetida debía rechazarse");}
        catch(ValidacionException esperado){ /* resultado esperado */ }
    }
    private static void sql(Connection con,String sql,int id) throws Exception {
        try(PreparedStatement ps=con.prepareStatement(sql)){ps.setInt(1,id);ps.executeUpdate();}
    }
}
