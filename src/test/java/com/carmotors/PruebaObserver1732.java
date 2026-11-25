package com.carmotors;

import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.OrdenServicioDAO;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.EventoServicioCompletado;
import com.carmotors.modelo.OrdenServicio;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicio.*;
import com.carmotors.servicio.observer.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Pruebas autocontenidas sin MySQL ni JUnit, para la entrega del examen. */
public final class PruebaObserver1732 {
    private static int comprobaciones;
    private PruebaObserver1732() {}

    public static void main(String[] args) throws Exception {
        Path carpeta = Files.createTempDirectory("carmotors-observer1732-");
        Path archivo = carpeta.resolve("notificaciones_clientes.txt");
        Escenario normal = new Escenario("En Proceso");
        var txt = new ObservadorNotificacionCliente(archivo);
        var log = new ObservadorLogServicio();
        normal.servicio.registrarObservador(log);
        normal.servicio.registrarObservador(txt);
        normal.servicio.registrarObservador(txt); // No debe duplicarse.
        ByteArrayOutputStream consola = new ByteArrayOutputStream();
        PrintStream salidaOriginal = System.out;
        try {
            System.setOut(new PrintStream(consola, true, StandardCharsets.UTF_8));
            normal.servicio.completarOrden(1);
        } finally {
            System.setOut(salidaOriginal);
        }
        comprobar(normal.commit == 1 && normal.rollback == 0, "La finalización se confirma una vez");
        comprobar("Completado".equals(normal.dao.orden.getEstado()), "Estado completado");
        comprobar(consola.toString(StandardCharsets.UTF_8).contains("LOG: El servicio # 1 para el vehículo ABC123 ha sido completado."), "Log en consola");
        List<String> lineas = Files.readAllLines(archivo);
        comprobar(lineas.size() == 1, "Un solo aviso aun con alta repetida del observador");
        comprobar(lineas.get(0).contains("[2026-11-25 10:15:30] - NOTIFICACIÓN PARA ANA: Su vehículo con placa ABC123 está listo para ser recogido."), "Formato fecha/cliente/placa");
        esperarFallo(() -> normal.servicio.completarOrden(1));
        comprobar(Files.readAllLines(archivo).size() == 1, "No hay aviso al repetir finalización");

        // La misma instancia añade, no sobrescribe.
        txt.actualizar(new EventoServicioCompletado(2, 1, "DEF456", 42, "LUIS", LocalDateTime.of(2026, 11, 25, 10, 20, 0)));
        comprobar(Files.readAllLines(archivo).size() == 2, "APPEND conserva el primer aviso");

        Escenario pendiente = new Escenario("Pendiente");
        AtomicInteger llamadas = new AtomicInteger();
        pendiente.servicio.registrarObservador(e -> llamadas.incrementAndGet());
        pendiente.servicio.iniciarOrden(1);
        comprobar(llamadas.get() == 0 && pendiente.commit == 1, "No notifica al iniciar");

        Escenario falloEstado = new Escenario("En Proceso");
        falloEstado.dao.fallarEstado = true;
        falloEstado.servicio.registrarObservador(e -> llamadas.incrementAndGet());
        esperarFallo(() -> falloEstado.servicio.completarOrden(1));
        comprobar(falloEstado.rollback == 1 && llamadas.get() == 0, "Rollback sin notificación por fallo del DAO");

        Escenario falloCommit = new Escenario("En Proceso");
        falloCommit.fallarCommit = true;
        falloCommit.servicio.registrarObservador(e -> llamadas.incrementAndGet());
        esperarFallo(() -> falloCommit.servicio.completarOrden(1));
        comprobar(falloCommit.rollback == 1 && llamadas.get() == 0, "Rollback sin notificación por fallo del commit");

        Escenario aislado = new Escenario("En Proceso");
        aislado.servicio.registrarObservador(e -> { throw new IllegalStateException("notificador caído"); });
        aislado.servicio.registrarObservador(e -> llamadas.incrementAndGet());
        aislado.servicio.completarOrden(1);
        comprobar(aislado.commit == 1 && llamadas.get() == 1, "Un observador fallido no impide commit ni segundo aviso");
        aislado.servicio.removerObservador(null); // eliminación inocua
        comprobar(aislado.commit == 1, "Permite quitar observadores");

        Escenario sinClientes = new Escenario("En Proceso", true);
        sinClientes.servicio.registrarObservador(e -> llamadas.incrementAndGet());
        esperarFallo(() -> sinClientes.servicio.completarOrden(1));
        comprobar(sinClientes.rollback == 1 && llamadas.get() == 1, "Sin cliente se revierte, no se publica evento incompleto");
        System.out.println("EXAMEN 1732: " + comprobaciones + " comprobaciones OK (sin MySQL)");
    }

    static void comprobar(boolean pasa, String caso) {
        if (!pasa) throw new AssertionError("FALLÓ: " + caso);
        System.out.println("OK: " + caso);
        comprobaciones++;
    }
    static void esperarFallo(Runnable runnable) {
        try { runnable.run(); throw new AssertionError("Debió fallar la transición"); }
        catch (ValidacionException | com.carmotors.excepcion.DaoException expected) { }
    }

    static final class Escenario {
        final Memoria dao = new Memoria();
        final Clock reloj = Clock.fixed(Instant.parse("2026-11-25T10:15:30Z"), ZoneOffset.UTC);
        final PublicadorInventario publicador = new PublicadorInventario();
        int commit, rollback;
        boolean fallarCommit;
        final OrdenServicioService servicio;

        Escenario(String estado) { this(estado, false); }
        Escenario(String estado, boolean clienteAusente) {
            dao.orden.setId(1);
            dao.orden.setVehiculoId(1);
            dao.orden.setClienteId(42);
            dao.orden.setPlaca("ABC123");
            dao.orden.setEstado(estado);
            dao.orden.setFechaRecepcion(LocalDateTime.of(2026, 11, 24, 9, 0));
            ClienteDAO clienteDAO = (ClienteDAO) Proxy.newProxyInstance(
                    ClienteDAO.class.getClassLoader(), new Class<?>[] { ClienteDAO.class }, (proxy, method, args) -> {
                        if (method.getName().equals("buscarPorId")) {
                            if (clienteAusente) return Optional.empty();
                            Cliente cliente = new Cliente("ANA", "123", "3001234567", null);
                            cliente.setId(42);
                            return Optional.of(cliente);
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
            VehiculoConsulta vehiculos = new VehiculoConsulta() {
                public Optional<Vehiculo> buscarPorId(int id) { return Optional.empty(); }
                public Optional<Vehiculo> buscarPorPlaca(String placa) { return Optional.empty(); }
            };
            servicio = new OrdenServicioService(dao, vehiculos, this::conexion, reloj,
                    (con, id) -> Set.of(5),
                    (con, id, fecha) -> { throw new AssertionError("No debía facturar"); },
                    publicador, clienteDAO);
        }
        Connection conexion() {
            String anterior = dao.orden.getEstado();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "setAutoCommit", "close" -> null;
                        case "getAutoCommit" -> false;
                        case "commit" -> {
                            if (fallarCommit) throw new SQLException("Fallo de commit inyectado");
                            commit++;
                            yield null;
                        }
                        case "rollback" -> {
                            rollback++;
                            dao.orden.setEstado(anterior);
                            yield null;
                        }
                        default -> throw new AssertionError("Operación JDBC inesperada: " + method.getName());
                    });
        }
    }
    static final class Memoria implements OrdenServicioDAO {
        final OrdenServicio orden = new OrdenServicio();
        boolean fallarEstado;
        public Optional<OrdenServicio> bloquearPorId(Connection c,int id) { return id==1?Optional.of(orden):Optional.empty(); }
        public boolean actualizarEstado(Connection c,int id,String estado,String nuevo,LocalDateTime fecha) {
            if (fallarEstado) return false;
            orden.setEstado(nuevo);
            return true;
        }
        public boolean existeFactura(Connection c,int id) { return false; }
        public boolean consumoCompleto(Connection c,int id) { return true; }
        public int crear(Connection c,OrdenServicio o) { throw new UnsupportedOperationException(); }
        public Optional<OrdenServicio> buscarPorId(Connection c,int id) { return bloquearPorId(c,id); }
        public List<OrdenServicio> listar(Connection c) { return List.of(orden); }
        public List<OrdenServicio> listarPorVehiculo(Connection c,int id) { return List.of(); }
        public List<OrdenServicio> listarPorEstado(Connection c,String estado) { return List.of(); }
    }
}
