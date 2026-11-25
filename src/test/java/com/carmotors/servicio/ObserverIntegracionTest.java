package com.carmotors.servicio;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.LoteDAOJdbc;
import com.carmotors.modelo.Lote;
import com.carmotors.modelo.LoteLinea;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Integración opcional: usa MySQL y elimina sus propios fixtures. */
class ObserverIntegracionTest {
    private final LocalDate fecha = LocalDate.of(2026, 11, 16);

    private boolean disponible() {
        try (Connection con = ConexionFactory.getInstancia().crearConexion()) {
            return con.isValid(2);
        } catch (Exception e) { return false; }
    }

    private LoteLinea linea(int repuesto, int cantidad) {
        LoteLinea linea = new LoteLinea();
        linea.setRepuestoId(repuesto);
        linea.setCantidadIngresada(cantidad);
        linea.setCostoUnitario(new BigDecimal("1000.00"));
        return linea;
    }

    private Lote lote(String codigo, LoteLinea... lineas) {
        Lote lote = new Lote();
        lote.setCodigo(codigo);
        lote.setProveedorId(1);
        lote.setFechaIngreso(fecha);
        for (LoteLinea linea : lineas) lote.agregarLinea(linea);
        return lote;
    }

    private void limpiar(String codigo) throws Exception {
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

    @Test void commitNotificaYRollbackNoNotifica() throws Exception {
        assumeTrue(disponible(), "No hay MySQL para integración");
        String ok = "OBS-OK-" + System.nanoTime();
        String error = "OBS-ERR-" + System.nanoTime();
        PublicadorInventario publicador = new PublicadorInventario();
        List<Integer> eventos = new ArrayList<>();
        publicador.suscribir(eventos::add);
        LoteService servicio = new LoteService(new LoteDAOJdbc(), new ProveedorConsultaMemoria(),
                () -> ConexionFactory.getInstancia().crearConexion(), () -> fecha, publicador);
        try {
            servicio.registrarIngreso(lote(ok, linea(4, 2)));
            assertEquals(List.of(4), eventos);
            // Primera línea válida; la segunda falla por clave foránea en MySQL.
            // La transacción debe revertirse completa y NO emitir eventos.
            assertThrows(RuntimeException.class,
                    () -> servicio.registrarIngreso(lote(error, linea(4, 2), linea(Integer.MAX_VALUE, 1))));
            assertEquals(List.of(4), eventos);
            assertTrue(servicio.buscarPorCodigo(error).isEmpty());
        } finally {
            limpiar(ok);
            limpiar(error);
        }
    }
}
