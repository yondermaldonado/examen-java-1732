package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.factory.TecnicoDAOFactory;
import com.carmotors.excepcion.ConexionException;
import com.carmotors.modelo.Tecnico;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TecnicoDAOJdbcIntegracionTest {
    @Test
    void crudUsaLaConexionYTransaccionDelLlamador() throws Exception {
        Connection abierta;
        try {
            abierta = ConexionFactory.getInstancia().crearConexion();
        } catch (ConexionException e) {
            assumeTrue(false, "MySQL local no disponible para probar DAO de técnicos.");
            return;
        }
        try (Connection con = abierta) {
            con.setAutoCommit(false);
            try {
                TecnicoDAO dao = new TecnicoDAOFactory().obtenerDAO();
                Tecnico tecnico = new Tecnico();
                tecnico.setNombre("Prueba Factory Method");
                tecnico.setDocumentoIdentidad("FM" + Long.toUnsignedString(System.nanoTime(), 36));
                tecnico.setEspecialidad("Mecánica");
                int id = dao.crear(con, tecnico);
                tecnico.setId(id);
                Tecnico guardado = dao.buscarPorId(con, id).orElseThrow();
                assertEquals(tecnico.getDocumentoIdentidad(), guardado.getDocumentoIdentidad());
                assertNull(guardado.getTelefono());
                assertEquals(id, dao.buscarPorDocumento(con, tecnico.getDocumentoIdentidad()).orElseThrow().getId());
                assertFalse(dao.tieneOrdenes(con, id));
                assertTrue(guardado.isActivo());
                tecnico.setNombre("Actualizado");
                tecnico.setActivo(false);
                assertTrue(dao.actualizar(con, tecnico));
                assertFalse(dao.buscarPorId(con, id).orElseThrow().isActivo());
                assertTrue(dao.listar(con).stream().anyMatch(t -> t.getId() == id));
                assertFalse(con.isClosed());
                con.rollback();
                assertTrue(dao.buscarPorId(con, id).isEmpty(), "El DAO no debe confirmar inserciones.");
                int otro = dao.crear(con, tecnico);
                assertTrue(dao.eliminar(con, otro));
                assertTrue(dao.buscarPorId(con, otro).isEmpty());
            } finally {
                con.rollback();
            }
        }
    }
}
