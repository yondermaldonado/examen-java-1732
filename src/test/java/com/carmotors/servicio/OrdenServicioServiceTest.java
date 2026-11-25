package com.carmotors.servicio;

import com.carmotors.controlador.OrdenServicioController;
import com.carmotors.dao.OrdenServicioDAO;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.Vehiculo;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdenServicioServiceTest {
    private final Vehiculo vehiculo = new Vehiculo("ABC123", 7, 1);
    private final Memoria dao = new Memoria();
    private final AtomicInteger aperturas = new AtomicInteger();
    private int commits, rollbacks, cierres;
    private boolean vehiculoExiste = true, fallaCommit;
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
    private final VehiculoConsulta consulta = new VehiculoConsulta() {
        public Optional<Vehiculo> buscarPorId(int id) { return vehiculoExiste && id == 9 ? Optional.of(vehiculo) : Optional.empty(); }
        public Optional<Vehiculo> buscarPorPlaca(String placa) { return Optional.empty(); }
    };
    private final OrdenServicioService servicio = new OrdenServicioService(dao, consulta, () -> {
        aperturas.incrementAndGet();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, metodo, args) -> {
                    switch (metodo.getName()) {
                        case "setAutoCommit": assertEquals(false, args[0]); return null;
                        case "commit": if (fallaCommit) throw new SQLException("Fallo de commit"); commits++; return null;
                        case "rollback": rollbacks++; return null;
                        case "close": cierres++; return null;
                        default: throw new AssertionError("JDBC inesperado: " + metodo.getName());
                    }
                });
    }, reloj);

    @Test void altaSiemprePendienteConPropietarioYFechaDelReloj() {
        OrdenServicio orden = servicio.crearOrden(9, " preventivo ", "  Cambio de aceite  ");
        assertEquals(1, orden.getId());
        assertEquals(7, orden.getClienteId());
        assertEquals("ABC123", orden.getPlaca());
        assertEquals("Preventivo", orden.getTipoMantenimiento());
        assertEquals("Pendiente", orden.getEstado());
        assertEquals("Cambio de aceite", orden.getDescripcionProblema());
        assertEquals(LocalDateTime.of(2026, 11, 17, 10, 0), orden.getFechaRecepcion());
        assertEquals(0, orden.getCostoManoObra().signum());
        assertNull(orden.getFechaCompletado());
        assertNull(orden.getFechaEntrega());
        assertEquals(1, commits);
        assertEquals(1, cierres);
    }

    @Test void aceptaCorrectivoYRechazaCamposInvalidosSinConexion() {
        assertThrows(ValidacionException.class, () -> servicio.crearOrden(0, "Preventivo", "Problema"));
        assertThrows(ValidacionException.class, () -> servicio.crearOrden(9, "Otro", "Problema"));
        assertThrows(ValidacionException.class, () -> servicio.crearOrden(9, null, "Problema"));
        assertThrows(ValidacionException.class, () -> servicio.crearOrden(9, "Correctivo", " "));
        assertThrows(ValidacionException.class, () -> servicio.crearOrden(9, "Correctivo", "á".repeat(32768)));
        assertEquals(0, aperturas.get());
        assertEquals("Correctivo", servicio.crearOrden(9, "correctivo", "Frenos").getTipoMantenimiento());
    }

    @Test void rechazaVehiculoInexistenteAntesDeGuardar() {
        vehiculoExiste = false;
        assertThrows(ValidacionException.class, () -> servicio.crearOrden(9, "Preventivo", "Problema"));
        assertEquals(0, aperturas.get());
        assertTrue(dao.ordenes.isEmpty());
    }

    @Test void consultaPorIdVehiculoYEstadoSinEscrituras() {
        servicio.crearOrden(9, "Preventivo", "Problema");
        assertEquals(1, servicio.listar().size());
        assertTrue(servicio.buscarPorId(1).isPresent());
        assertTrue(servicio.buscarPorId(999).isEmpty());
        assertEquals(1, servicio.listarPorVehiculo(9).size());
        assertTrue(servicio.listarPorVehiculo(2).isEmpty());
        assertEquals(1, servicio.listarPorEstado(" pendiente ").size());
        assertTrue(servicio.listarPorEstado("Entregado").isEmpty());
        assertEquals(1, commits);
    }

    @Test void consultaInvalidaNoAbreConexion() {
        assertThrows(ValidacionException.class, () -> servicio.buscarPorId(-1));
        assertThrows(ValidacionException.class, () -> servicio.listarPorVehiculo(0));
        assertThrows(ValidacionException.class, () -> servicio.listarPorEstado("Recibido"));
        assertEquals(0, aperturas.get());
    }

    @Test void errorDePersistenciaYCommitHacenRollback() {
        dao.falla = true;
        assertThrows(DaoException.class, () -> servicio.crearOrden(9, "Preventivo", "Problema"));
        dao.falla = false; fallaCommit = true;
        assertThrows(DaoException.class, () -> servicio.crearOrden(9, "Preventivo", "Problema"));
        assertEquals(2, rollbacks);
        assertEquals(0, commits);
        assertEquals(2, cierres);
    }

    @Test void controladorDevuelveMensajesYNoLeeConsola() {
        OrdenServicioController controller = new OrdenServicioController(servicio);
        assertTrue(controller.listar().contains("No hay órdenes"));
        assertTrue(controller.crear(null, "Preventivo", "Problema").startsWith("Error:"));
        assertTrue(controller.crear("9", "Preventivo", "Problema").contains("Pendiente"));
        assertTrue(controller.buscarPorId("1").contains("cliente: #7"));
        assertTrue(controller.buscarPorId("99").contains("No existe"));
        assertTrue(controller.listarPorVehiculo("9").contains("ABC123"));
        assertTrue(controller.listarPorEstado("desconocido").startsWith("Error:"));
        dao.falla = true;
        assertTrue(controller.crear("9", "Preventivo", "Problema").startsWith("Error del sistema:"));
    }

    private static class Memoria implements OrdenServicioDAO {
        final List<OrdenServicio> ordenes = new ArrayList<>();
        boolean falla;
        public Optional<OrdenServicio> bloquearPorId(Connection c, int id) { throw new UnsupportedOperationException(); }
        public boolean actualizarEstado(Connection c, int id, String a, String n, LocalDateTime fecha) { throw new UnsupportedOperationException(); }
        public boolean existeFactura(Connection c, int id) { throw new UnsupportedOperationException(); }
        public boolean consumoCompleto(Connection c, int id) { throw new UnsupportedOperationException(); }
        public int crear(Connection con, OrdenServicio o) {
            if (falla) throw new DaoException("Error de prueba");
            ordenes.add(o); return ordenes.size();
        }
        public Optional<OrdenServicio> buscarPorId(Connection c, int id) { return ordenes.stream().filter(o -> o.getId() == id).findFirst(); }
        public List<OrdenServicio> listar(Connection c) { return List.copyOf(ordenes); }
        public List<OrdenServicio> listarPorVehiculo(Connection c, int id) { return ordenes.stream().filter(o -> o.getVehiculoId() == id).toList(); }
        public List<OrdenServicio> listarPorEstado(Connection c, String estado) { return ordenes.stream().filter(o -> o.getEstado().equals(estado)).toList(); }
    }
}
