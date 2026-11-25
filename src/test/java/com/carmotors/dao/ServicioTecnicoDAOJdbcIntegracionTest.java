package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.factory.ServicioTecnicoDAOFactory;
import com.carmotors.excepcion.ConexionException;
import com.carmotors.excepcion.DaoException;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.Tecnico;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServicioTecnicoDAOJdbcIntegracionTest {
    @Test void relacionCompuestaUnicaYConsultasSinCommitImplicito() throws Exception {
        Connection abierta;
        try { abierta = ConexionFactory.getInstancia().crearConexion(); }
        catch (ConexionException e) { assumeTrue(false, "MySQL local no disponible."); return; }
        try (Connection con = abierta) {
            con.setAutoCommit(false);
            try {
                int vehiculo;
                try (PreparedStatement ps = con.prepareStatement("SELECT id FROM vehiculos ORDER BY id LIMIT 1"); ResultSet rs = ps.executeQuery()) {
                    assumeTrue(rs.next(), "Cargar un vehículo de prueba."); vehiculo = rs.getInt(1);
                }
                OrdenServicio o = new OrdenServicio(); o.setVehiculoId(vehiculo); o.setTipoMantenimiento("Preventivo");
                o.setEstado("Pendiente"); o.setFechaRecepcion(LocalDateTime.now()); o.setDescripcionProblema("Prueba de asignación");
                int orden = new OrdenServicioDAOJdbc().crear(con,o);
                Tecnico t = new Tecnico(); t.setNombre("Prueba N:M");
                t.setDocumentoIdentidad("AS"+Long.toUnsignedString(System.nanoTime(),36));
                int tecnico = new TecnicoDAOJdbc().crear(con,t);
                ServicioTecnicoDAO dao = new ServicioTecnicoDAOFactory().obtenerDAO();
                assertEquals("Pendiente",dao.bloquearEstadoOrden(con,orden).orElseThrow());
                assertTrue(dao.bloquearTecnicoActivo(con,tecnico).orElseThrow());
                assertTrue(dao.listarSinOrdenesAbiertas(con).stream().anyMatch(f -> f.tecnicoId() == tecnico));
                dao.asignar(con,orden,tecnico);
                assertFalse(dao.listarSinOrdenesAbiertas(con).stream().anyMatch(f -> f.tecnicoId() == tecnico));
                assertTrue(dao.listarCargaEnProceso(con).stream().noneMatch(f -> f.tecnicoId() == tecnico));
                try (PreparedStatement ps = con.prepareStatement("UPDATE servicios SET estado_id = (SELECT id FROM servicio_estados WHERE nombre = ?) WHERE id = ?")) {
                    ps.setString(1, "En Proceso"); ps.setInt(2, orden); ps.executeUpdate();
                    assertEquals(1, dao.listarCargaEnProceso(con).stream().filter(f -> f.tecnicoId() == tecnico).findFirst().orElseThrow().ordenesEnProceso());
                    ps.setString(1, "Completado"); ps.executeUpdate();
                    assertTrue(dao.listarSinOrdenesAbiertas(con).stream().anyMatch(f -> f.tecnicoId() == tecnico));
                    assertTrue(dao.listarCargaEnProceso(con).stream().noneMatch(f -> f.tecnicoId() == tecnico));
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE servicios SET estado_id = (SELECT id FROM servicio_estados WHERE nombre = 'Pendiente') WHERE id = ?")) {
                    ps.setInt(1, orden); ps.executeUpdate();
                }
                dao.iniciarOrden(con, orden);
                assertEquals("En Proceso", dao.bloquearEstadoOrden(con, orden).orElseThrow());
                dao.iniciarOrden(con, orden);
                assertEquals("En Proceso", dao.bloquearEstadoOrden(con, orden).orElseThrow());
                assertEquals(new BigDecimal("0.00"), dao.bloquearHoras(con, orden, tecnico).orElseThrow());
                assertThrows(DaoException.class, () -> dao.asignar(con,orden,tecnico));
                assertTrue(dao.existe(con,orden,tecnico));
                dao.actualizarHoras(con,orden,tecnico,new BigDecimal("2.75"));
                assertEquals(0,new BigDecimal("2.75").compareTo(dao.listarPorOrden(con,orden).get(0).horasTrabajadas()));
                assertEquals(1,dao.listarPorTecnico(con,tecnico).size());
                assertTrue(dao.quitar(con,orden,tecnico)); assertFalse(dao.existe(con,orden,tecnico));
                dao.asignar(con,orden,tecnico);
                con.rollback();
                assertTrue(dao.listarPorOrden(con,orden).isEmpty());
                assertTrue(new TecnicoDAOJdbc().buscarPorId(con,tecnico).isEmpty());
            } finally { con.rollback(); }
        }
    }
}
