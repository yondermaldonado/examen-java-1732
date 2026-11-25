package com.carmotors.servicio;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.carmotors.dao.RepuestoDAOJdbc;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Repuesto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Sin base de datos: toda validación debe fallar ANTES de pedir una conexión. */
class RepuestoServiceTest {

    private final RepuestoService servicio = new RepuestoService(
            new RepuestoDAOJdbc(),
            new ProveedorConsultaMemoria(),
            () -> { throw new AssertionError("No debe abrir conexión si el dato es inválido"); });

    private Repuesto valido() {
        Repuesto r = new Repuesto();
        r.setNombre("Filtro de prueba");
        r.setTipoId(2);
        r.setMarcaId(7);
        r.setProveedorId(1);
        r.setPrecioCompra(new BigDecimal("15000.00"));
        r.setPrecioVenta(new BigDecimal("25000.00"));
        r.setStockMinimo(3);
        return r;
    }

    private void rechaza(Repuesto r) {
        assertThrows(ValidacionException.class, () -> servicio.crear(r));
    }

    @Test void rechazaNulo()               { rechaza(null); }
    @Test void rechazaNombreVacio()        { Repuesto r = valido(); r.setNombre("   "); rechaza(r); }
    @Test void rechazaNombreLargo()        { Repuesto r = valido(); r.setNombre("a".repeat(256)); rechaza(r); }
    @Test void rechazaTipoInvalido()       { Repuesto r = valido(); r.setTipoId(0); rechaza(r); }
    @Test void rechazaMarcaInvalida()      { Repuesto r = valido(); r.setMarcaId(0); rechaza(r); }
    @Test void rechazaProveedorInexistente() { Repuesto r = valido(); r.setProveedorId(99); rechaza(r); }
    @Test void rechazaPrecioVentaNegativo() { Repuesto r = valido(); r.setPrecioVenta(new BigDecimal("-5")); rechaza(r); }
    @Test void rechazaPrecioCompraNegativo() { Repuesto r = valido(); r.setPrecioCompra(new BigDecimal("-0.01")); rechaza(r); }
    @Test void rechazaPrecioNulo()         { Repuesto r = valido(); r.setPrecioVenta(null); rechaza(r); }
    @Test void rechazaTresDecimales()      { Repuesto r = valido(); r.setPrecioVenta(new BigDecimal("10.123")); rechaza(r); }
    @Test void rechazaPrecioDemasiadoGrande() { Repuesto r = valido(); r.setPrecioVenta(new BigDecimal("100000000.00")); rechaza(r); }
    @Test void rechazaStockMinimoNegativo() { Repuesto r = valido(); r.setStockMinimo(-1); rechaza(r); }

    @Test
    void actualizarRechazaIdInvalido() {
        Repuesto r = valido(); // id = 0
        assertThrows(ValidacionException.class, () -> servicio.actualizar(r));
    }

    @Test
    void eliminarRechazaIdInvalido() {
        assertThrows(ValidacionException.class, () -> servicio.eliminar(0));
    }
}