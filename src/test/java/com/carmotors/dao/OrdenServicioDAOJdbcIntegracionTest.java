package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.factory.OrdenServicioDAOFactory;
import com.carmotors.excepcion.ConexionException;
import com.carmotors.modelo.OrdenServicio;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OrdenServicioDAOJdbcIntegracionTest {
    @Test void registraConsultaCalculaManoObraYRespetaRollback() throws Exception {
        Connection abierta;
        try { abierta = ConexionFactory.getInstancia().crearConexion(); }
        catch (ConexionException e) { assumeTrue(false, "MySQL local no disponible."); return; }
        try (Connection con = abierta) {
            con.setAutoCommit(false);
            try {
                int vehiculoId, clienteId, catalogoId;
                try (PreparedStatement ps = con.prepareStatement("SELECT id, cliente_id FROM vehiculos ORDER BY id LIMIT 1"); ResultSet rs = ps.executeQuery()) {
                    assumeTrue(rs.next(), "Cargar fixtures de vehículos primero.");
                    vehiculoId = rs.getInt(1); clienteId = rs.getInt(2);
                }
                try (PreparedStatement ps = con.prepareStatement("SELECT id FROM servicios_catalogo ORDER BY id LIMIT 1"); ResultSet rs = ps.executeQuery()) {
                    assumeTrue(rs.next(), "Cargar catálogo de operaciones primero."); catalogoId = rs.getInt(1);
                }
                OrdenServicioDAO dao = new OrdenServicioDAOFactory().obtenerDAO();
                OrdenServicio o = new OrdenServicio(); o.setVehiculoId(vehiculoId);
                o.setEstado("Pendiente"); o.setTipoMantenimiento("Preventivo");
                o.setFechaRecepcion(LocalDateTime.of(2026, 11, 17, 10, 0));
                o.setDescripcionProblema("Prueba JDBC de órdenes");
                int id = dao.crear(con, o);
                OrdenServicio guardada = dao.buscarPorId(con, id).orElseThrow();
                assertEquals(clienteId, guardada.getClienteId());
                assertEquals(0, guardada.getCostoManoObra().signum());
                assertEquals("Pendiente", dao.bloquearPorId(con, id).orElseThrow().getEstado());
                assertFalse(dao.existeFactura(con, id));
                assertTrue(dao.consumoCompleto(con, id)); // Sin líneas no hay repuestos pendientes.
                assertTrue(dao.actualizarEstado(con, id, "Pendiente", "En Proceso", o.getFechaRecepcion()));
                assertFalse(dao.actualizarEstado(con, id, "Pendiente", "Completado", o.getFechaRecepcion()));

                try (PreparedStatement ps = con.prepareStatement("INSERT INTO servicios_detalle (servicio_id, servicio_catalogo_id, precio_cobrado) VALUES (?, ?, ?)")) {
                    ps.setInt(1, id); ps.setInt(2, catalogoId); ps.setBigDecimal(3, new BigDecimal("123.45")); ps.executeUpdate();
                }
                assertEquals(0, new BigDecimal("123.45").compareTo(dao.buscarPorId(con, id).orElseThrow().getCostoManoObra()));
                assertTrue(dao.listarPorEstado(con, "En Proceso").stream().anyMatch(x -> x.getId() == id));
                assertTrue(dao.listarPorVehiculo(con, vehiculoId).stream().anyMatch(x -> x.getId() == id));
                con.rollback();
                assertTrue(dao.buscarPorId(con, id).isEmpty());
            } finally { con.rollback(); }
        }
    }
}
