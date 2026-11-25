package com.carmotors.servicio;

import com.carmotors.dao.OrdenServicioDAO;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.AsignacionLote;
import com.carmotors.modelo.ExistenciaLote;
import com.carmotors.modelo.LineaRepuestoOrden;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.ReservaOrden;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservasOrdenServiceTest {
    private final Clock reloj=Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"),ZoneOffset.UTC);
    private final List<Integer> eventos=new ArrayList<>();
    private final List<ReservaOrden> reservas=new ArrayList<>();
    private LineaRepuestoOrden linea;
    private String estado="En Proceso";
    private int commits,rollbacks,cierres,descuentos;
    private final Connection conexion=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
        switch(m.getName()) {
            case "getAutoCommit":return false;
            case "setAutoCommit":return null;
            case "commit":commits++;return null;
            case "rollback":rollbacks++;return null;
            case "close":cierres++;return null;
            default:throw new AssertionError(m.getName());
        }
    });
    private final OrdenServicioDAO ordenes=(OrdenServicioDAO)Proxy.newProxyInstance(OrdenServicioDAO.class.getClassLoader(),new Class<?>[]{OrdenServicioDAO.class},(p,m,a)->{
        if(!m.getName().equals("bloquearPorId"))throw new AssertionError(m.getName());
        OrdenServicio o=new OrdenServicio();o.setId(1);o.setEstado(estado);return Optional.of(o);
    });
    private final InventarioReservas inventario=(InventarioReservas)Proxy.newProxyInstance(InventarioReservas.class.getClassLoader(),new Class<?>[]{InventarioReservas.class},(p,m,a)->{
        assertSame(conexion,a[0]);
        switch(m.getName()) {
            case "bloquearPrecio":return Optional.of(new BigDecimal("100.00"));
            case "bloquearLineas":return linea==null?List.of():List.of(linea);
            case "bloquearReservas":return List.copyOf(reservas);
            case "bloquearLotesDisponibles":return List.of(lote(8));
            case "liberarActivas":reservas.clear();return null;
            case "guardarLinea":linea=(LineaRepuestoOrden)a[2];return null;
            case "reservar":
                AsignacionLote lote=(AsignacionLote)a[2];
                reservas.add(new ReservaOrden(1,lote.repuestoId(),lote.loteId(),lote.cantidad(),"ACTIVA",(LocalDateTime)a[3]));return null;
            case "descontarLote":descuentos+=(Integer)a[3];return true;
            case "consumirReserva":return true;
            default:throw new AssertionError(m.getName());
        }
    });
    private ReservasOrdenService servicio(){
        PublicadorInventario publicador=new PublicadorInventario();
        publicador.suscribir(id->{assertTrue(commits>0);eventos.add(id);});
        return new ReservasOrdenService(ordenes,inventario,()->conexion,reloj,publicador);
    }
    private ExistenciaLote lote(int disponible){
        return new ExistenciaLote(10,"L",1,"Proveedor",LocalDate.of(2026,11,1),7,"Repuesto",disponible,disponible,disponible,BigDecimal.TEN,null);
    }

    @Test void reservaNoDescuentaYNotificaDespuesDeCommit(){
        servicio().reservar(1,7,3);
        assertEquals(3,linea.cantidad());assertEquals(new BigDecimal("100.00"),linea.precioUnitario());
        assertEquals(3,reservas.get(0).cantidad());assertEquals(0,descuentos);
        assertEquals(List.of(7),eventos);assertEquals(1,commits);assertEquals(1,cierres);
    }
    @Test void ajusteMantienePrecioHistorico(){
        linea=new LineaRepuestoOrden(7,2,new BigDecimal("50.00"));
        servicio().ajustar(1,7,4);
        assertEquals(new BigDecimal("50.00"),linea.precioUnitario());assertEquals(4,linea.cantidad());
    }
    @Test void consumoNoConfirmaCierraNiPublicaLaTransaccionAjena(){
        linea=new LineaRepuestoOrden(7,3,BigDecimal.TEN);
        reservas.add(new ReservaOrden(1,7,10,3,"ACTIVA",LocalDateTime.of(2026,11,17,9,0)));
        Set<Integer> ids=servicio().consumir(conexion,1);
        assertEquals(Set.of(7),ids);assertEquals(3,descuentos);
        assertEquals(0,commits);assertEquals(0,cierres);assertTrue(eventos.isEmpty());
    }
    @Test void invalidaCerradaOConsumoRepetidoNoEscribe(){
        assertThrows(ValidacionException.class,()->servicio().reservar(0,7,1));
        assertThrows(ValidacionException.class,()->servicio().reservar(1,7,0));
        assertEquals(0,cierres);
        estado="Completado";
        assertThrows(ValidacionException.class,()->servicio().reservar(1,7,1));
        assertEquals(1,rollbacks);assertTrue(eventos.isEmpty());
        estado="En Proceso";linea=new LineaRepuestoOrden(7,1,BigDecimal.TEN);
        reservas.add(new ReservaOrden(1,7,10,1,"CONSUMIDA",LocalDateTime.of(2026,11,17,9,0)));
        assertThrows(ValidacionException.class,()->servicio().consumir(conexion,1));
        assertEquals(0,descuentos);
    }
    @Test void fefoNoDesbordaAlSumarGrandesExistencias(){
        ExistenciaLote a=lote(Integer.MAX_VALUE);
        ExistenciaLote b=new ExistenciaLote(11,"B",1,"P",LocalDate.of(2026,11,2),7,"R",10,10,10,BigDecimal.TEN,null);
        assertEquals(1,PoliticaFefo.asignar(List.of(a,b),1,LocalDate.now(reloj)).get(0).cantidad());
    }
}
