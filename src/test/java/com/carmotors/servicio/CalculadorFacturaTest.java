package com.carmotors.servicio;

import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.DatosOrdenParaFactura;
import com.carmotors.modelo.DatosOrdenParaFactura.ManoObra;
import com.carmotors.modelo.DatosOrdenParaFactura.Repuesto;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculadorFacturaTest {
    @Test
    void calculaDetalleConPreciosHistoricosYRedondeaImpuestoSobreElSubtotal() {
        var detalle = new DatosOrdenParaFactura(
                List.of(new Repuesto(1, "Filtro", 3, new BigDecimal("3.20")),
                        new Repuesto(2, "Bujía", 2, new BigDecimal("0.10"))),
                List.of(new ManoObra(1, "Revisión", new BigDecimal("0.10")),
                        new ManoObra(2, "Montaje", new BigDecimal("0.15"))));
        var ordenesCompletadas = Map.of(16L, detalle);
        var calculador = new CalculadorFactura(id -> Optional.ofNullable(ordenesCompletadas.get(id)),
                new BigDecimal("0.10"));

        var factura = calculador.calcular(16);

        assertEquals(16L, factura.ordenId());
        assertEquals(detalle, factura.detalle());
        assertEquals(new BigDecimal("3.20"), factura.detalle().repuestos().get(0).precioUnitarioHistorico());
        assertEquals(new BigDecimal("9.60"), factura.detalle().repuestos().get(0).importe());
        assertEquals(new BigDecimal("9.80"), factura.totalRepuestos());
        assertEquals(new BigDecimal("0.25"), factura.totalManoObra());
        assertEquals(new BigDecimal("0.10"), factura.tasaImpuesto());
        assertEquals(new BigDecimal("1.01"), factura.impuestos());
        assertEquals(new BigDecimal("11.06"), factura.totalFinal());
    }

    @Test
    void admiteTasaCeroYConservaDosDecimalesEnTotalesVacios() {
        var detalle = new DatosOrdenParaFactura(List.of(), List.of());
        var factura = new CalculadorFactura(id -> Optional.of(detalle), BigDecimal.ZERO).calcular(1);

        assertEquals(new BigDecimal("0.00"), factura.totalRepuestos());
        assertEquals(new BigDecimal("0.00"), factura.totalManoObra());
        assertEquals(new BigDecimal("0.00"), factura.impuestos());
        assertEquals(new BigDecimal("0.00"), factura.totalFinal());

        var gravable = new DatosOrdenParaFactura(List.of(),
                List.of(new ManoObra(1, "Montaje", new BigDecimal("1.04"))));
        assertEquals(new BigDecimal("0.00"),
                new CalculadorFactura(id -> Optional.of(gravable), BigDecimal.ZERO).calcular(1).impuestos());
        assertEquals(new BigDecimal("0.10"),
                new CalculadorFactura(id -> Optional.of(gravable), new BigDecimal("0.10")).calcular(1).impuestos());
    }

    @Test
    void rechazaIdentificadorInvalidoYOrdenFueraDelPuertoDeCompletadas() {
        var calculador = new CalculadorFactura(id -> {
            assertEquals(9L, id);
            return Optional.empty();
        }, BigDecimal.ZERO);

        assertThrows(ValidacionException.class, () -> calculador.calcular(0));
        assertThrows(ValidacionException.class, () -> calculador.calcular(9));
    }

    @Test
    void rechazaTasasYDetallesInvalidos() {
        ConsultaOrdenCompletada consulta = id -> Optional.empty();
        assertThrows(ValidacionException.class, () -> new CalculadorFactura(consulta, null));
        assertThrows(ValidacionException.class, () -> new CalculadorFactura(consulta, new BigDecimal("-0.01")));
        assertThrows(ValidacionException.class, () -> new CalculadorFactura(consulta, new BigDecimal("1.01")));
        assertThrows(ValidacionException.class, () -> new Repuesto(1, "Filtro", 0, BigDecimal.ONE));
        assertThrows(ValidacionException.class, () -> new Repuesto(0, "Filtro", 1, BigDecimal.ONE));
        assertThrows(ValidacionException.class, () -> new Repuesto(1, "Filtro", 1, null));
        assertThrows(ValidacionException.class,
                () -> new Repuesto(1, "Filtro", 1, new BigDecimal("1.005")));
        assertThrows(ValidacionException.class, () -> new ManoObra(1, "Revisión", new BigDecimal("-0.01")));
        assertThrows(ValidacionException.class, () -> new ManoObra(1, " ", BigDecimal.ONE));
        assertThrows(ValidacionException.class, () -> new DatosOrdenParaFactura(null, List.of()));
    }
}
