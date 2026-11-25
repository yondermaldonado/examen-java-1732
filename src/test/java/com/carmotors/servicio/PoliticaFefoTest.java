package com.carmotors.servicio;

import static org.junit.jupiter.api.Assertions.*;

import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.AsignacionLote;
import com.carmotors.modelo.ExistenciaLote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Sin base de datos: la política FEFO es lógica pura. "Hoy" es siempre 2026-11-13. */
class PoliticaFefoTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 13);

    private ExistenciaLote lote(int id, String ingreso, String caducidad, int disponible) {
        return new ExistenciaLote(id, "L" + id, 1, "Proveedor", LocalDate.parse(ingreso),
                1, "Repuesto", disponible, disponible, disponible,
                new BigDecimal("1000.00"), caducidad == null ? null : LocalDate.parse(caducidad));
    }

    @Test
    void tomaPrimeroElLoteQueCaducaAntes() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-01-01", "2027-01-10", 5),
                lote(2, "2026-02-01", "2026-12-01", 5));
        List<AsignacionLote> plan = PoliticaFefo.asignar(candidatos, 3, HOY);
        assertEquals(1, plan.size());
        assertEquals(2, plan.get(0).loteId());
        assertEquals(3, plan.get(0).cantidad());
    }

    @Test
    void losLotesSinCaducidadVanAlFinal() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-01-01", null, 10),
                lote(2, "2026-02-01", "2027-01-01", 2));
        List<AsignacionLote> plan = PoliticaFefo.asignar(candidatos, 4, HOY);
        assertEquals(2, plan.size());
        assertEquals(2, plan.get(0).loteId());
        assertEquals(2, plan.get(0).cantidad());
        assertEquals(1, plan.get(1).loteId());
        assertEquals(2, plan.get(1).cantidad());
    }

    @Test
    void entreLotesSinCaducidadSaleElMasAntiguo() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-03-01", null, 5),
                lote(2, "2026-01-01", null, 5));
        assertEquals(2, PoliticaFefo.asignar(candidatos, 1, HOY).get(0).loteId());
    }

    @Test
    void conLaMismaCaducidadSaleElDeIngresoMasAntiguo() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-05-01", "2027-01-01", 5),
                lote(2, "2026-02-01", "2027-01-01", 5));
        assertEquals(2, PoliticaFefo.asignar(candidatos, 1, HOY).get(0).loteId());
    }

    @Test
    void repartaLaCantidadEntreLotes() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-01-01", "2027-01-01", 3),
                lote(2, "2026-01-01", "2027-06-01", 10));
        List<AsignacionLote> plan = PoliticaFefo.asignar(candidatos, 5, HOY);
        assertEquals(2, plan.size());
        assertEquals(3, plan.get(0).cantidad());
        assertEquals(2, plan.get(1).cantidad());
    }

    @Test
    void excluyeLotesVencidosPeroNoLosQueVencenHoy() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-01-01", "2026-11-12", 10),   // venció ayer
                lote(2, "2026-01-01", "2026-11-13", 1));   // vence hoy: todavía sirve
        List<AsignacionLote> plan = PoliticaFefo.asignar(candidatos, 1, HOY);
        assertEquals(1, plan.size());
        assertEquals(2, plan.get(0).loteId());
        assertThrows(ValidacionException.class, () -> PoliticaFefo.asignar(candidatos, 2, HOY));
    }

    @Test
    void ignoraLotesSinUnidadesDisponibles() {
        List<ExistenciaLote> candidatos = List.of(
                lote(1, "2026-01-01", "2026-12-01", 0),
                lote(2, "2026-01-01", "2027-01-01", 4));
        List<AsignacionLote> plan = PoliticaFefo.asignar(candidatos, 2, HOY);
        assertEquals(1, plan.size());
        assertEquals(2, plan.get(0).loteId());
    }

    @Test
    void rechazaCuandoNoHayStockSuficiente() {
        List<ExistenciaLote> candidatos = List.of(lote(1, "2026-01-01", "2027-01-01", 3));
        assertThrows(ValidacionException.class, () -> PoliticaFefo.asignar(candidatos, 4, HOY));
    }

    @Test
    void rechazaSinCandidatos() {
        assertThrows(ValidacionException.class, () -> PoliticaFefo.asignar(List.of(), 1, HOY));
    }

    @Test
    void rechazaCantidadCeroONegativa() {
        List<ExistenciaLote> candidatos = List.of(lote(1, "2026-01-01", null, 3));
        assertThrows(ValidacionException.class, () -> PoliticaFefo.asignar(candidatos, 0, HOY));
        assertThrows(ValidacionException.class, () -> PoliticaFefo.asignar(candidatos, -2, HOY));
    }
}