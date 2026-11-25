package com.carmotors.servicio;

import com.carmotors.dao.FacturaDAO;
import com.carmotors.dao.ReporteServiciosDAO;
import com.carmotors.dao.factory.DAOFactory;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.DatosOrdenParaFactura;
import com.carmotors.modelo.FacturaDTO;
import com.carmotors.modelo.FiltroReporte;
import com.carmotors.modelo.OrdenReporte;
import com.carmotors.modelo.SolicitudServicioReporteDato;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServiciosFactoryMethodTest {
    @Test void reporteUsaProductoIntercambiableYConservaOrdenYFiltro() {
        AtomicBoolean cerrada=new AtomicBoolean();
        Connection con=conexion(false,cerrada);
        LocalDate fecha=LocalDate.of(2026,11,18);
        FiltroReporte filtro=new FiltroReporte(fecha,fecha,OrdenReporte.CANTIDAD_DESC);
        DAOFactory<ReporteServiciosDAO> creador=new DAOFactory<>() {
            @Override protected ReporteServiciosDAO crearDAO() {
                return (conexion,recibido)->{
                    assertSame(con,conexion);assertSame(filtro,recibido);
                    return List.of(new SolicitudServicioReporteDato(1,"Preventivo",fecha),
                            new SolicitudServicioReporteDato(2,"Preventivo",fecha),
                            new SolicitudServicioReporteDato(3,"Correctivo",fecha));
                };
            }
        };
        var estrategia=new ServiciosMasSolicitadosStrategy(creador.obtenerDAO(),()->con);
        var resultado=estrategia.generar(filtro);
        assertEquals("Preventivo",resultado.filas().get(0).nombre());
        assertEquals(2,resultado.filas().get(0).cantidad());
        assertEquals(1,resultado.filas().get(1).cantidad());assertTrue(cerrada.get());
    }

    @Test void facturaUsaElProductoSeleccionadoSinCerrarNiConfirmarLaConexionAjena() {
        AtomicBoolean cerrada=new AtomicBoolean();Connection con=conexion(false,cerrada);
        AtomicReference<FacturaDTO> guardada=new AtomicReference<>();
        LocalDateTime fecha=LocalDateTime.of(2026,11,18,15,0);
        DAOFactory<FacturaDAO> creador=new DAOFactory<>() {
            @Override protected FacturaDAO crearDAO() {
                return new FacturaDAO() {
                    public Optional<DatosOrdenParaFactura> buscarOrdenCompletada(Connection recibida,long id) {
                        assertSame(con,recibida);assertEquals(9,id);
                        return Optional.of(new DatosOrdenParaFactura(List.of(),List.of(
                                new DatosOrdenParaFactura.ManoObra(1,"Revisión",new BigDecimal("100.00")))));
                    }
                    public void insertar(Connection recibida,FacturaDTO factura,LocalDateTime emision) {
                        assertSame(con,recibida);assertSame(fecha,emision);guardada.set(factura);
                    }
                };
            }
        };
        new FacturacionService(creador.obtenerDAO(),new BigDecimal("0.19")).facturar(con,9,fecha);
        assertEquals(new BigDecimal("119.00"),guardada.get().totalFinal());
        assertFalse(cerrada.get());
    }

    @Test void facturaSinTransaccionNoInvocaElProducto() {
        FacturaDAO prohibido=new FacturaDAO() {
            public Optional<DatosOrdenParaFactura> buscarOrdenCompletada(Connection con,long id) {throw new AssertionError("No debe consultar");}
            public void insertar(Connection con,FacturaDTO factura,LocalDateTime fecha) {throw new AssertionError("No debe insertar");}
        };
        assertThrows(ValidacionException.class,()->new FacturacionService(prohibido,BigDecimal.ZERO)
                .facturar(conexion(true,new AtomicBoolean()),9,LocalDateTime.now()));
    }

    private Connection conexion(boolean autoCommit,AtomicBoolean cerrada) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("getAutoCommit"))return autoCommit;
            if(m.getName().equals("close")){cerrada.set(true);return null;}
            throw new AssertionError("El servicio no debe usar "+m.getName()+" en esta prueba");
        });
    }
}
