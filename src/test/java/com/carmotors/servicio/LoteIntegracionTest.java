package com.carmotors.servicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.LoteDAOJdbc;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.AsignacionLote;
import com.carmotors.modelo.ExistenciaLote;
import com.carmotors.modelo.Lote;
import com.carmotors.modelo.LoteLinea;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Necesita MySQL con schema.sql y data.sql cargados. Si no hay servidor, se omite. "Hoy" es 2026-11-13. */
class LoteIntegracionTest {

    private static LoteService servicio;

    @BeforeAll
    static void prepararOOmitir() {
        boolean disponible;
        try (Connection con = ConexionFactory.getInstancia().crearConexion()) {
            disponible = con.isValid(2);
        } catch (Exception e) {
            disponible = false;
        }
        assumeTrue(disponible, "MySQL no disponible: se omiten las pruebas de integración");
        servicio = new LoteService(new LoteDAOJdbc(), new ProveedorConsultaMemoria(),
                () -> ConexionFactory.getInstancia().crearConexion(),
                () -> LocalDate.of(2026, 11, 13));
    }

    // ---------- Apoyo ----------

    private String codigoUnico() {
        return "TEST-" + System.nanoTime();
    }

    private LoteLinea linea(int repuestoId, int cantidad, LocalDate caducidad) {
        LoteLinea l = new LoteLinea();
        l.setRepuestoId(repuestoId);
        l.setCantidadIngresada(cantidad);
        l.setCostoUnitario(new BigDecimal("12000"));
        l.setFechaCaducidad(caducidad);
        return l;
    }

    private Lote lote(String codigo, LoteLinea... lineas) {
        Lote lote = new Lote();
        lote.setCodigo(codigo);
        lote.setProveedorId(1);
        lote.setFechaIngreso(LocalDate.of(2026, 11, 13));
        for (LoteLinea l : lineas) {
            lote.agregarLinea(l);
        }
        return lote;
    }

    /** Limpieza de las pruebas (SQL permitido aquí: es código de prueba, no de la aplicación). */
    private void borrar(String codigo) throws Exception {
        try (Connection con = ConexionFactory.getInstancia().crearConexion()) {
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM lotes_repuestos WHERE lote_id IN (SELECT id FROM lotes WHERE codigo = ?)")) {
                ps.setString(1, codigo);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM lotes WHERE codigo = ?")) {
                ps.setString(1, codigo);
                ps.executeUpdate();
            }
        }
    }

    // ---------- Pruebas ----------

    @Test
    void ingresoSinCaducidadQuedaTrazableYSumaAlStock() throws Exception {
        String codigo = codigoUnico();
        int stockAntes = servicio.stockFisico(4);
        try {
            int id = servicio.registrarIngreso(lote(codigo, linea(4, 7, null)));
            assertTrue(id > 0);

            List<ExistenciaLote> encontrado = servicio.buscarPorCodigo(codigo);
            assertEquals(1, encontrado.size());
            ExistenciaLote e = encontrado.get(0);
            assertEquals("Repuestos del Oriente S.A.S.", e.proveedor());
            assertEquals(LocalDate.of(2026, 11, 13), e.fechaIngreso());
            assertEquals(7, e.cantidadIngresada());
            assertEquals(7, e.cantidadActual());           // al ingresar, actual = ingresada
            assertEquals(7, e.cantidadDisponible());
            assertNull(e.fechaCaducidad());                // sin caducidad admitido
            assertEquals(new BigDecimal("12000.00"), e.costoUnitario());

            assertEquals(stockAntes + 7, servicio.stockFisico(4));
        } finally {
            borrar(codigo);
        }
        assertEquals(stockAntes, servicio.stockFisico(4));
    }

    @Test
    void ingresoConVariasLineasYCaducidad() throws Exception {
        String codigo = codigoUnico();
        try {
            servicio.registrarIngreso(lote(codigo,
                    linea(1, 5, LocalDate.of(2027, 5, 1)),
                    linea(2, 3, null)));
            List<ExistenciaLote> lineas = servicio.buscarPorCodigo(codigo);
            assertEquals(2, lineas.size());
            assertEquals(LocalDate.of(2027, 5, 1), lineas.get(0).fechaCaducidad());
            assertNull(lineas.get(1).fechaCaducidad());
        } finally {
            borrar(codigo);
        }
    }

    @Test
    void rechazaCodigoDuplicadoSinGuardarNada() throws Exception {
        String codigo = codigoUnico();
        try {
            servicio.registrarIngreso(lote(codigo, linea(4, 2, null)));
            int stockConPrimero = servicio.stockFisico(4);
            ValidacionException error = assertThrows(ValidacionException.class,
                    () -> servicio.registrarIngreso(lote(codigo, linea(4, 9, null))));
            assertTrue(error.getMessage().contains("código"));
            assertEquals(stockConPrimero, servicio.stockFisico(4));
        } finally {
            borrar(codigo);
        }
    }

    @Test
    void siFallaUnaLineaNoQuedaNadaGuardado() throws Exception {
        String codigo = codigoUnico();
        int stockAntes = servicio.stockFisico(4);
        try {
            // La segunda línea usa un repuesto que no existe: toda la transacción se deshace
            assertThrows(ValidacionException.class,
                    () -> servicio.registrarIngreso(lote(codigo, linea(4, 5, null), linea(999, 5, null))));
            assertTrue(servicio.buscarPorCodigo(codigo).isEmpty());
            assertEquals(stockAntes, servicio.stockFisico(4));
        } finally {
            borrar(codigo);
        }
    }

    @Test
    void trazabilidadPorRepuestoMuestraLoteYProveedor() {
        List<ExistenciaLote> lotes = servicio.listarPorRepuesto(1);   // aceite 5W-30 en data.sql
        assertEquals(2, lotes.size());
        assertEquals("L-2026-001", lotes.get(0).codigo());
        assertEquals("Lubricantes Andinos Ltda.", lotes.get(0).proveedor());
        assertEquals("L-2026-005", lotes.get(1).codigo());
        // L-2026-001: 4 actuales - 3 reservadas ACTIVAS = 1 disponible
        assertEquals(4, lotes.get(0).cantidadActual());
        assertEquals(1, lotes.get(0).cantidadDisponible());
    }

    @Test
    void trazabilidadPorProveedor() {
        // Proveedor 2 (Lubricantes Andinos): lote 1 con 2 repuestos y lote 6 con 1 = 3 filas
        assertEquals(3, servicio.listarPorProveedor(2).size());
    }

    @Test
    void elStockSeCalculaDesdeLosLotes() {
        // Repuesto 1 en data.sql: 4 + 12 = 16 físico; 5 reservadas ACTIVAS = 11 disponible
        assertEquals(16, servicio.stockFisico(1));
        assertEquals(11, servicio.stockDisponible(1));
    }

    @Test
    void fefoReparteEntreLotesEnOrdenDeCaducidad() {
        List<AsignacionLote> plan = servicio.seleccionarFefo(1, 5);
        assertEquals(2, plan.size());
        assertEquals("L-2026-001", plan.get(0).codigo());   // caduca 2028-08-20
        assertEquals(1, plan.get(0).cantidad());            // solo 1 disponible
        assertEquals("L-2026-005", plan.get(1).codigo());   // caduca 2029-10-20
        assertEquals(4, plan.get(1).cantidad());
    }

    @Test
    void fefoNoUsaLotesVencidos() {
        // Repuesto 11: el lote L-2025-014 venció el 2026-10-31 (3 unidades); solo sirve L-2026-003
        List<AsignacionLote> plan = servicio.seleccionarFefo(11, 5);
        assertEquals(1, plan.size());
        assertEquals("L-2026-003", plan.get(0).codigo());
        assertThrows(ValidacionException.class, () -> servicio.seleccionarFefo(11, 11)); // 10 vigentes
    }

    @Test
    void fefoRechazaSiTodoEstaReservado() {
        // Repuesto 8: 2 físicas y 2 reservadas ACTIVAS = 0 disponibles
        assertThrows(ValidacionException.class, () -> servicio.seleccionarFefo(8, 1));
    }
}