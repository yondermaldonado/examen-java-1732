package com.carmotors.servicio;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carmotors.dao.LoteDAOJdbc;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Lote;
import com.carmotors.modelo.LoteLinea;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Sin base de datos: toda validación debe fallar ANTES de pedir una conexión. "Hoy" es 2026-11-13. */
class LoteServiceTest {

    private final LoteService servicio = new LoteService(
            new LoteDAOJdbc(),
            new ProveedorConsultaMemoria(),
            () -> { throw new AssertionError("No debe abrir conexión si el dato es inválido"); },
            () -> LocalDate.of(2026, 11, 13));

    private LoteLinea linea(int repuestoId) {
        LoteLinea l = new LoteLinea();
        l.setRepuestoId(repuestoId);
        l.setCantidadIngresada(10);
        l.setCostoUnitario(new BigDecimal("15000.00"));
        l.setFechaCaducidad(LocalDate.of(2027, 6, 30));
        return l;
    }

    private Lote valido() {
        Lote lote = new Lote();
        lote.setCodigo("L-PRUEBA-1");
        lote.setProveedorId(1);
        lote.setFechaIngreso(LocalDate.of(2026, 11, 10));
        lote.agregarLinea(linea(3));
        return lote;
    }

    private void rechaza(Lote lote) {
        assertThrows(ValidacionException.class, () -> servicio.registrarIngreso(lote));
    }

    @Test void rechazaNulo()                 { rechaza(null); }
    @Test void rechazaCodigoVacio()          { Lote l = valido(); l.setCodigo("  "); rechaza(l); }
    @Test void rechazaCodigoLargo()          { Lote l = valido(); l.setCodigo("L".repeat(51)); rechaza(l); }
    @Test void rechazaProveedorInvalido()    { Lote l = valido(); l.setProveedorId(0); rechaza(l); }
    @Test void rechazaProveedorInexistente() { Lote l = valido(); l.setProveedorId(99); rechaza(l); }
    @Test void rechazaIngresoSinFecha()      { Lote l = valido(); l.setFechaIngreso(null); rechaza(l); }
    @Test void rechazaIngresoFuturo()        { Lote l = valido(); l.setFechaIngreso(LocalDate.of(2026, 11, 14)); rechaza(l); }
    @Test void rechazaSinLineas()            { Lote l = valido(); l.getLineas().clear(); rechaza(l); }
    @Test void rechazaRepuestoInvalido()     { Lote l = valido(); l.getLineas().get(0).setRepuestoId(0); rechaza(l); }
    @Test void rechazaCantidadCero()         { Lote l = valido(); l.getLineas().get(0).setCantidadIngresada(0); rechaza(l); }
    @Test void rechazaCantidadNegativa()     { Lote l = valido(); l.getLineas().get(0).setCantidadIngresada(-5); rechaza(l); }
    @Test void rechazaCostoNulo()            { Lote l = valido(); l.getLineas().get(0).setCostoUnitario(null); rechaza(l); }
    @Test void rechazaCostoNegativo()        { Lote l = valido(); l.getLineas().get(0).setCostoUnitario(new BigDecimal("-1")); rechaza(l); }
    @Test void rechazaCostoConTresDecimales() { Lote l = valido(); l.getLineas().get(0).setCostoUnitario(new BigDecimal("10.123")); rechaza(l); }
    @Test void rechazaCaducidadAnteriorAlIngreso() {
        Lote l = valido();
        l.getLineas().get(0).setFechaCaducidad(LocalDate.of(2026, 11, 9)); // un día antes del ingreso
        rechaza(l);
    }
    @Test void rechazaRepuestoRepetidoEnElLote() {
        Lote l = valido();
        l.agregarLinea(linea(3));
        rechaza(l);
    }

    @Test
    void seleccionarFefoRechazaDatosInvalidosSinAbrirConexion() {
        assertThrows(ValidacionException.class, () -> servicio.seleccionarFefo(0, 5));
        assertThrows(ValidacionException.class, () -> servicio.seleccionarFefo(1, 0));
    }
}