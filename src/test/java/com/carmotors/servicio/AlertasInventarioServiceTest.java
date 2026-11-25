package com.carmotors.servicio;

import com.carmotors.dao.AlertasDAO;
import com.carmotors.dao.ExistenciasDAO;
import com.carmotors.modelo.AlertaInventario;
import com.carmotors.modelo.RepuestoMinimo;
import com.carmotors.modelo.StockLote;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlertasInventarioServiceTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);
    private final List<StockLote> lotes = List.of(
            new StockLote(101, "L-A", 7, HOY.plusDays(3), 10, 4),
            new StockLote(102, "L-B", 7, null, 6, 2),
            new StockLote(103, "L-C", 7, HOY.minusDays(1), 5, 0),
            new StockLote(104, "L-D", 7, HOY.plusDays(8), 2, 0),
            new StockLote(105, "L-E", 7, HOY, 1, 0));

    private final ExistenciasDAO existenciasDAO = new ExistenciasDAO() {
        @Override public List<StockLote> porRepuesto(Connection c, int id) {
            return id == 7 ? lotes : List.of();
        }
        @Override public List<StockLote> porLote(Connection c, int loteId, int id) {
            return lotes.stream().filter(l -> l.loteId() == loteId && l.repuestoId() == id).toList();
        }
    };
    private final AlertasDAO alertasDAO = c -> List.of(new RepuestoMinimo(7, "Filtro", 14),
                                                       new RepuestoMinimo(8, "Sin lotes", 0));

    private AlertasInventarioService crear(int dias) {
        ExistenciasService existencias = new ExistenciasService(existenciasDAO, () -> null, () -> HOY);
        return new AlertasInventarioService(alertasDAO, existencias, () -> null, () -> HOY, dias);
    }

    @Test void detectaStockBajoConReservasYExcluyeVencidos() {
        List<AlertaInventario> alertas = crear(7).consultarAlertas();
        AlertaInventario bajo = alertas.stream().filter(a -> a.getTipo().equals("STOCK_BAJO")).findFirst().orElseThrow();
        assertEquals(7, bajo.getRepuestoId());
        assertEquals(13, bajo.getCantidad()); // 6 + 4 + 2 + 1 (vencido excluido)
        assertEquals(14, bajo.getMinimo());
    }

    @Test void caducidadOpcionalVencidaYUmbral() {
        List<AlertaInventario> alertas = crear(7).consultarAlertas();
        assertEquals(2, alertas.stream().filter(a -> a.getTipo().equals("CADUCIDAD_PROXIMA")).count());
        assertTrue(alertas.stream().anyMatch(a -> "L-A".equals(a.getCodigoLote())));
        assertTrue(alertas.stream().anyMatch(a -> "L-E".equals(a.getCodigoLote())));
        assertFalse(alertas.stream().anyMatch(a -> "L-B".equals(a.getCodigoLote()) || "L-C".equals(a.getCodigoLote()) || "L-D".equals(a.getCodigoLote())));
    }

    @Test void cambiarUmbralCambiaLaLista() {
        assertEquals(1, crear(0).consultarAlertas().stream()
                .filter(a -> a.getTipo().equals("CADUCIDAD_PROXIMA")).count());
        assertEquals(3, crear(8).consultarAlertas().stream()
                .filter(a -> a.getTipo().equals("CADUCIDAD_PROXIMA")).count());
    }

    @Test void suscribirDesuscribirYReporte() {
        AlertasInventarioService observador = crear(7);
        PublicadorInventario publicador = new PublicadorInventario();
        publicador.suscribir(observador);
        assertTrue(observador.ultimasAlertas().isEmpty());
        publicador.publicar(7);
        assertEquals(3, observador.ultimasAlertas().size());
        publicador.desuscribir(observador);
        publicador.publicar(7);
        assertTrue(observador.reporteConsola().contains("STOCK BAJO"));
        assertTrue(observador.reporteConsola().contains("CADUCIDAD PROXIMA"));
    }

    @Test void fallaUnObservadorNoImpideNotificarOtro() {
        PublicadorInventario publicador = new PublicadorInventario();
        int[] recibidos = {0};
        publicador.suscribir(id -> { throw new IllegalStateException("Error de prueba"); });
        publicador.suscribir(id -> recibidos[0]++);
        publicador.publicar(7);
        assertEquals(1, recibidos[0]);
    }

    @Test void diasNegativosNoPermitidos() {
        assertThrows(RuntimeException.class, () -> crear(-1));
    }
}
