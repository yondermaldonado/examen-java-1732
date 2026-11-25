package com.carmotors.servicio;

import com.carmotors.dao.ExistenciasDAO;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.StockLote;
import com.carmotors.modelo.StockRepuesto;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fixture independiente del módulo de órdenes y sin MySQL. */
class ExistenciasServiceTest {
    private static final LocalDate HOY = LocalDate.of(2026, 11, 16);
    private final List<StockLote> fixtures = List.of(
            new StockLote(101, "STOCK-101", 7, LocalDate.of(2026, 12, 1), 10, 4),
            new StockLote(102, "STOCK-102", 7, null, 6, 2),
            new StockLote(103, "STOCK-103", 7, LocalDate.of(2026, 11, 15), 5, 0));
    private final ExistenciasDAO dao = new ExistenciasDAO() {
        @Override public List<StockLote> porRepuesto(Connection c, int id) {
            return id == 7 ? fixtures : List.of();
        }
        @Override public List<StockLote> porLote(Connection c, int loteId, int repuestoId) {
            return fixtures.stream().filter(l -> l.loteId() == loteId && l.repuestoId() == repuestoId).toList();
        }
    };
    // El DAO de prueba usa los datos en memoria y no necesita una conexión real.
    // try-with-resources admite null y no intenta cerrar ninguna conexión.
    private final ExistenciasService servicio = new ExistenciasService(dao, () -> null, () -> HOY);

    @Test void resumenRestaSoloActivasYSeparaCaducados() {
        StockRepuesto r = servicio.resumen(7);
        assertEquals(21, r.fisico());
        assertEquals(6, r.reservadoActivo());
        assertEquals(15, r.disponible());
        assertEquals(10, r.disponibleUtilizable());
    }
    @Test void detallePorLoteMantieneLasUnidades() {
        StockLote lote = servicio.porLote(101, 7).orElseThrow();
        assertEquals(10, lote.fisico());
        assertEquals(4, lote.reservadoActivo());
        assertEquals(6, lote.disponible());
    }
    @Test void loteVencidoSigueFisicoPeroNoEsUtilizable() {
        StockLote lote = servicio.porLote(103, 7).orElseThrow();
        assertEquals(5, lote.disponible());
        assertEquals(0, lote.disponibleUtilizable(HOY));
    }
    @Test void sinCaducidadYVenceHoySonVigentes() {
        assertTrue(servicio.porLote(102, 7).orElseThrow().vigente(HOY));
        assertTrue(new StockLote(104, "HOY", 7, HOY, 1, 0).vigente(HOY));
    }
    @Test void sinLotesDevuelveCeros() {
        assertEquals(0, servicio.resumen(999).disponible());
        assertTrue(servicio.porLote(999, 7).isEmpty());
    }
    @Test void validaIdsAntesDeAbrirConexion() {
        assertThrows(ValidacionException.class, () -> servicio.resumen(0));
        assertThrows(ValidacionException.class, () -> servicio.porLote(0, 7));
    }
    @Test void rechazaDatosInconsistentes() {
        assertThrows(IllegalArgumentException.class, () -> new StockLote(1, "A", 7, null, 2, 3));
    }
}
