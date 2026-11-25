package com.carmotors.servicio;

import com.carmotors.controlador.TecnicoController;
import com.carmotors.dao.TecnicoDAO;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Tecnico;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TecnicoServiceTest {
    private final Memoria dao = new Memoria();
    private final AtomicInteger aperturas = new AtomicInteger();
    private int commits, rollbacks, cierres;
    private boolean fallaCommit;
    private final TecnicoService servicio = new TecnicoService(dao, () -> {
        aperturas.incrementAndGet();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "setAutoCommit": assertEquals(false, args[0]); return null;
                        case "commit":
                            if (fallaCommit) throw new SQLException("Fallo de commit", "08006");
                            commits++; return null;
                        case "rollback": rollbacks++; return null;
                        case "close": cierres++; return null;
                        default: throw new AssertionError("Llamada inesperada: " + metodo.getName());
                    }
                });
    });

    private Tecnico datos(String documento) {
        Tecnico t = new Tecnico();
        t.setNombre("  Ana Pérez  ");
        t.setDocumentoIdentidad(documento);
        t.setTelefono(" ");
        t.setEspecialidad("  Mecánica  ");
        return t;
    }

    @Test void registraNormalizaYConsultaSinAbrirTransaccionesDeEscritura() {
        int id = servicio.registrar(datos("  DOC1  "));
        Tecnico t = servicio.buscarPorDocumento(" DOC1 ").orElseThrow();
        assertEquals("Ana Pérez", t.getNombre());
        assertNull(t.getTelefono());
        assertEquals("Mecánica", t.getEspecialidad());
        assertTrue(t.isActivo());
        assertEquals(id, servicio.buscarPorId(id).orElseThrow().getId());
        assertEquals(1, servicio.listar().size());
        assertEquals(1, commits);
        assertEquals(4, cierres);
        assertEquals(0, rollbacks);
    }

    @Test void rechazaDatosInvalidosAntesDeAbrirConexion() {
        assertThrows(ValidacionException.class, () -> servicio.registrar(null));
        Tecnico t = datos("D");
        t.setNombre(" ");
        Tecnico nombreVacio = t;
        assertThrows(ValidacionException.class, () -> servicio.registrar(nombreVacio));
        assertThrows(ValidacionException.class, () -> servicio.registrar(datos("x".repeat(21))));
        t = datos("D"); t.setTelefono("x".repeat(21));
        Tecnico telefonoLargo = t;
        assertThrows(ValidacionException.class, () -> servicio.registrar(telefonoLargo));
        Tecnico especialidadLarga = datos("D"); especialidadLarga.setEspecialidad("x".repeat(151));
        assertThrows(ValidacionException.class, () -> servicio.registrar(especialidadLarga));
        assertThrows(ValidacionException.class, () -> servicio.buscarPorId(0));
        assertThrows(ValidacionException.class, () -> servicio.buscarPorDocumento(null));
        assertEquals(0, aperturas.get());
    }

    @Test void documentoDuplicadoRevierteSinInsertarOtroTecnico() {
        servicio.registrar(datos("DOC"));
        assertThrows(ValidacionException.class, () -> servicio.registrar(datos("DOC")));
        assertEquals(1, dao.filas.size());
        assertEquals(1, commits);
        assertEquals(1, rollbacks);
    }

    @Test void actualizaSuDocumentoPeroRechazaElDeOtroTecnico() {
        int id = servicio.registrar(datos("A"));
        servicio.registrar(datos("B"));
        Tecnico modificado = datos("A"); modificado.setId(id); modificado.setActivo(false);
        assertTrue(servicio.actualizar(modificado));
        assertFalse(servicio.buscarPorId(id).orElseThrow().isActivo());
        modificado.setDocumentoIdentidad("B");
        assertThrows(ValidacionException.class, () -> servicio.actualizar(modificado));
        assertEquals("A", servicio.buscarPorId(id).orElseThrow().getDocumentoIdentidad());
        Tecnico inexistente = datos("C"); inexistente.setId(999);
        assertFalse(servicio.actualizar(inexistente));
    }

    @Test void impideEliminarAsignadoYPermiteDesactivarlo() {
        int id = servicio.registrar(datos("D"));
        dao.asignado = true;
        assertThrows(ValidacionException.class, () -> servicio.eliminar(id));
        assertTrue(dao.filas.containsKey(id));
        Tecnico t = datos("D"); t.setId(id); t.setActivo(false);
        assertTrue(servicio.actualizar(t));
        dao.asignado = false;
        assertTrue(servicio.eliminar(id));
        assertFalse(servicio.eliminar(id));
    }

    @Test void restriccionesConcurrentesSeTraducenYHacenRollback() {
        dao.error = new DaoException("Duplicado", new SQLException("UK", "23000", 1062));
        assertThrows(ValidacionException.class, () -> servicio.registrar(datos("D")));
        dao.error = new DaoException("Relacionado", new SQLException("FK", "23000", 1451));
        assertThrows(ValidacionException.class, () -> servicio.eliminar(1));
        assertEquals(2, rollbacks);
        assertEquals(0, commits);
        assertEquals(2, cierres);
    }

    @Test void falloTecnicoYFalloDeCommitNoSePresentanComoExito() {
        dao.error = new DaoException("Fallo técnico");
        assertThrows(DaoException.class, () -> servicio.registrar(datos("D")));
        dao.error = null;
        fallaCommit = true;
        assertThrows(DaoException.class, () -> servicio.registrar(datos("D")));
        assertEquals(2, rollbacks);
        assertEquals(0, commits);
        assertEquals(2, cierres);
    }

    @Test void controladorManejaEntradaInvalidaVaciosYResultados() {
        TecnicoController controller = new TecnicoController(servicio);
        assertEquals("No hay técnicos registrados.", controller.listar());
        assertTrue(controller.buscarPorId("abc").startsWith("Error:"));
        assertTrue(controller.buscarPorId(null).startsWith("Error:"));
        assertTrue(controller.registrar("", "D", "", "").startsWith("Error:"));
        assertTrue(controller.registrar("Ana", "D", "", "Mecánica").contains("registrado"));
        assertTrue(controller.buscarPorDocumento("D").contains("Ana"));
        assertTrue(controller.actualizar("1", "Ana", "D", "", "Mecánica", false).contains("actualizado"));
        assertTrue(controller.buscarPorId("1").contains("Inactivo"));
        assertTrue(controller.buscarPorId("999").contains("No existe"));
        dao.asignado = true;
        assertTrue(controller.eliminar("1").contains("órdenes relacionadas"));
    }

    /** Doble persistente en memoria; la gestión JDBC se verifica por separado. */
    private static class Memoria implements TecnicoDAO {
        Map<Integer,Tecnico> filas = new LinkedHashMap<>();
        int secuencia;
        boolean asignado;
        DaoException error;
        private void comprobar() { if (error != null) throw error; }
        private Tecnico copia(Tecnico t) {
            Tecnico c = new Tecnico(); c.setId(t.getId()); c.setNombre(t.getNombre());
            c.setDocumentoIdentidad(t.getDocumentoIdentidad()); c.setTelefono(t.getTelefono());
            c.setEspecialidad(t.getEspecialidad()); c.setActivo(t.isActivo()); return c;
        }
        public int crear(Connection c, Tecnico t) {
            comprobar(); Tecnico guardado = copia(t); guardado.setId(++secuencia);
            filas.put(secuencia, guardado); return secuencia;
        }
        public Optional<Tecnico> buscarPorId(Connection c, int id) { return Optional.ofNullable(filas.get(id)).map(this::copia); }
        public Optional<Tecnico> buscarPorDocumento(Connection c, String doc) {
            return filas.values().stream().filter(t -> t.getDocumentoIdentidad().equals(doc)).findFirst().map(this::copia);
        }
        public List<Tecnico> listar(Connection c) { return filas.values().stream().map(this::copia).toList(); }
        public boolean actualizar(Connection c, Tecnico t) { comprobar(); return filas.replace(t.getId(), copia(t)) != null; }
        public boolean eliminar(Connection c, int id) { comprobar(); return filas.remove(id) != null; }
        public boolean tieneOrdenes(Connection c, int id) { return asignado; }
    }
}
