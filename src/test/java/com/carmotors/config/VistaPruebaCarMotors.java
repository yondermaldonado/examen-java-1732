package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.ClienteController;
import com.carmotors.controlador.HistorialController;
import com.carmotors.controlador.VehiculoController;
import com.carmotors.dao.factory.ClienteDAOFactory;
import com.carmotors.dao.factory.ClienteVehiculosDAOFactory;
import com.carmotors.dao.factory.HistorialServiciosDAOFactory;
import com.carmotors.dao.factory.VehiculoDAOFactory;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.OperacionHistorial;
import com.carmotors.modelo.RecordatorioPreventivo;
import com.carmotors.modelo.ServicioHistorial;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicio.ClienteService;
import com.carmotors.servicio.HistorialService;
import com.carmotors.servicio.HistorialServiciosConsultaJdbc;
import com.carmotors.servicio.RecordatorioPreventivoService;
import com.carmotors.servicio.VehiculoService;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;

/** Consola MANUAL para probar los modulos sin modificar las pruebas automaticas. */
public class VistaPruebaCarMotors {

    private final Scanner scanner = new Scanner(System.in);
    private final ClienteController clientes;
    private final VehiculoController vehiculos;
    private final HistorialController historiales;

    public VistaPruebaCarMotors() {
        com.carmotors.dao.ClienteDAO clienteDAO = new ClienteDAOFactory().obtenerDAO();
        VehiculoService vehiculoService = new VehiculoService(
                new VehiculoDAOFactory().obtenerDAO(), clienteDAO);

        clientes = new ClienteController(new ClienteService(
                clienteDAO, new ClienteVehiculosDAOFactory().obtenerDAO()));
        vehiculos = new VehiculoController(vehiculoService);

        HistorialService historialService = new HistorialService(
                vehiculoService,
                new HistorialServiciosConsultaJdbc(
                        new HistorialServiciosDAOFactory().obtenerDAO()));

        historiales = new HistorialController(
                historialService,
                new RecordatorioPreventivoService(
                        historialService, Clock.systemDefaultZone()));
    }

    public static void main(String[] args) {
        verificarBaseDePruebas();
        new VistaPruebaCarMotors().iniciar();
    }

    private static void verificarBaseDePruebas() {
        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {
            if (!"carmotors_test".equalsIgnoreCase(conexion.getCatalog())) {
                throw new IllegalStateException(
                        "La vista de prueba solo se ejecuta con carmotors_test");
            }
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No fue posible verificar la base de pruebas", e);
        }
    }

    private void iniciar() {
        System.out.println("\nCAR MOTORS - CONSOLA DE PRUEBA (carmotors_test)");
        System.out.println("Atencion: las opciones 8 a 13 MODIFICAN datos reales de prueba.");

        boolean continuar = true;
        while (continuar) {
            mostrarMenu();
            String opcion = leer("Opcion: ");
            try {
                switch (opcion) {
                    case "1": listarClientes(); break;
                    case "2": buscarCliente(); break;
                    case "3": listarVehiculosDeCliente(); break;
                    case "4": buscarVehiculo(); break;
                    case "5": verHistorialVehiculo(); break;
                    case "6": verRecordatoriosVehiculo(); break;
                    case "7": verResumenCliente(); break;
                    case "8": registrarCliente(); break;
                    case "9": registrarVehiculo(); break;
                    case "10": actualizarCliente(); break;
                    case "11": actualizarVehiculo(); break;
                    case "12": eliminarCliente(); break;
                    case "13": eliminarVehiculo(); break;
                    case "0": continuar = false; break;
                    default: System.out.println("Opcion no valida.");
                }
            } catch (RuntimeException e) {
                System.out.println("[ERROR] " + e.getMessage());
            }
        }
        System.out.println("Prueba finalizada.");
    }

    private void mostrarMenu() {
        System.out.println("\n========== MENU ==========");
        System.out.println(" 1. Listar clientes");
        System.out.println(" 2. Buscar cliente por documento");
        System.out.println(" 3. Ver vehiculos de un cliente");
        System.out.println(" 4. Buscar vehiculo por placa");
        System.out.println(" 5. Consultar historial de un vehiculo");
        System.out.println(" 6. Consultar recordatorios de un vehiculo");
        System.out.println(" 7. Ficha completa de un cliente");
        System.out.println("-- OPERACIONES QUE CAMBIAN DATOS DE PRUEBA --");
        System.out.println(" 8. Registrar cliente");
        System.out.println(" 9. Registrar vehiculo");
        System.out.println("10. Actualizar cliente");
        System.out.println("11. Actualizar vehiculo");
        System.out.println("12. Eliminar cliente");
        System.out.println("13. Eliminar vehiculo");
        System.out.println(" 0. Salir");
    }

    private void listarClientes() {
        List<Cliente> lista = clientes.listarClientes();
        if (lista.isEmpty()) {
            System.out.println("No hay clientes registrados.");
            return;
        }
        for (Cliente cliente : lista) mostrarCliente(cliente);
    }

    private void buscarCliente() {
        String documento = leer("Documento del cliente: ");
        Optional<Cliente> cliente =
                clientes.buscarClientePorDocumento(documento);
        if (cliente.isPresent()) {
            mostrarCliente(cliente.get());
        } else {
            System.out.println("No se encontro ese documento.");
        }
    }

    private void listarVehiculosDeCliente() {
        int clienteId = leerId("ID del cliente: ");
        mostrarCliente(clientes.obtenerClientePorId(clienteId));
        List<Vehiculo> lista =
                vehiculos.listarVehiculosPorCliente(clienteId);
        if (lista.isEmpty()) {
            System.out.println("El cliente no tiene vehiculos.");
        }
        for (Vehiculo vehiculo : lista) mostrarVehiculo(vehiculo);
    }

    private void buscarVehiculo() {
        String placa = leer("Placa: ");
        Optional<Vehiculo> vehiculo =
                vehiculos.buscarVehiculoPorPlaca(placa);
        if (vehiculo.isPresent()) {
            mostrarVehiculo(vehiculo.get());
            mostrarCliente(clientes.obtenerClientePorId(
                    vehiculo.get().getClienteId()));
        } else {
            System.out.println("No se encontro esa placa.");
        }
    }

    private void verHistorialVehiculo() {
        int vehiculoId = leerId("ID del vehiculo: ");
        mostrarVehiculo(vehiculos.obtenerVehiculoPorId(vehiculoId));
        mostrarServicios(
                historiales.consultarHistorialVehiculo(vehiculoId));
    }

    private void verRecordatoriosVehiculo() {
        int vehiculoId = leerId("ID del vehiculo: ");
        mostrarVehiculo(vehiculos.obtenerVehiculoPorId(vehiculoId));
        mostrarRecordatorios(
                historiales.consultarRecordatoriosVehiculo(vehiculoId));
    }

    private void verResumenCliente() {
        int clienteId = leerId("ID del cliente: ");
        mostrarCliente(clientes.obtenerClientePorId(clienteId));

        List<Vehiculo> lista =
                vehiculos.listarVehiculosPorCliente(clienteId);
        Map<Integer, List<ServicioHistorial>> servicios =
                historiales.consultarHistorialCliente(clienteId);
        Map<Integer, List<RecordatorioPreventivo>> recordatorios =
                historiales.consultarRecordatoriosCliente(clienteId);

        if (lista.isEmpty()) {
            System.out.println("El cliente no tiene vehiculos.");
        }
        for (Vehiculo vehiculo : lista) {
            System.out.println("\n---------------------------------");
            mostrarVehiculo(vehiculo);
            mostrarServicios(servicios.getOrDefault(
                    vehiculo.getId(), List.of()));
            mostrarRecordatorios(recordatorios.getOrDefault(
                    vehiculo.getId(), List.of()));
        }
    }

    private void registrarCliente() {
        String nombre = leer("Nombre: ");
        String documento = leer("Documento: ");
        String telefono = leer("Telefono: ");
        String email = leer("Email (opcional): ");
        if (!confirmarCambio()) return;
        Cliente cliente = clientes.registrarCliente(
                nombre, documento, telefono,
                email.isBlank() ? null : email);
        System.out.println("Cliente registrado:");
        mostrarCliente(cliente);
    }

    private void registrarVehiculo() {
        int clienteId = leerId("ID del cliente propietario: ");
        mostrarCliente(clientes.obtenerClientePorId(clienteId));
        String placa = leer("Placa: ");
        int modeloId = leerId("ID del modelo de vehiculo: ");
        if (!confirmarCambio()) return;
        Vehiculo vehiculo = vehiculos.registrarVehiculo(
                placa, clienteId, modeloId);
        System.out.println("Vehiculo registrado:");
        mostrarVehiculo(vehiculo);
    }

    private void actualizarCliente() {
        int id = leerId("ID del cliente: ");
        Cliente anterior = clientes.obtenerClientePorId(id);
        mostrarCliente(anterior);
        System.out.println("Enter para conservar cada dato.");
        String nombre = leerConActual("Nombre", anterior.getNombre());
        String documento = leerConActual(
                "Documento", anterior.getDocumento());
        String telefono = leerConActual("Telefono", anterior.getTelefono());
        String email = leerConActual("Email", anterior.getEmail());
        if (!confirmarCambio()) return;
        mostrarCliente(clientes.actualizarCliente(
                id, nombre, documento, telefono, email));
    }

    private void actualizarVehiculo() {
        int id = leerId("ID del vehiculo: ");
        Vehiculo anterior = vehiculos.obtenerVehiculoPorId(id);
        mostrarVehiculo(anterior);
        System.out.println("Enter para conservar cada dato.");
        String placa = leerConActual("Placa", anterior.getPlaca());
        int clienteId = leerEnteroConActual(
                "ID propietario", anterior.getClienteId());
        int modeloId = leerEnteroConActual(
                "ID modelo", anterior.getModeloId());
        if (!confirmarCambio()) return;
        mostrarVehiculo(vehiculos.actualizarVehiculo(
                id, placa, clienteId, modeloId));
    }

    private void eliminarCliente() {
        int id = leerId("ID del cliente: ");
        mostrarCliente(clientes.obtenerClientePorId(id));
        if (!confirmarEliminacion()) return;
        clientes.eliminarCliente(id);
        System.out.println("Cliente eliminado.");
    }

    private void eliminarVehiculo() {
        int id = leerId("ID del vehiculo: ");
        mostrarVehiculo(vehiculos.obtenerVehiculoPorId(id));
        if (!confirmarEliminacion()) return;
        vehiculos.eliminarVehiculo(id);
        System.out.println("Vehiculo eliminado.");
    }

    private void mostrarCliente(Cliente c) {
        System.out.println("Cliente #" + c.getId() + " | " + c.getNombre()
                + " | Documento: " + c.getDocumento()
                + " | Tel: " + c.getTelefono()
                + " | Email: " + (c.getEmail() == null ? "-" : c.getEmail()));
    }

    private void mostrarVehiculo(Vehiculo v) {
        System.out.println("Vehiculo #" + v.getId()
                + " | Placa: " + v.getPlaca()
                + " | Cliente: " + v.getClienteId()
                + " | Modelo ID: " + v.getModeloId());
    }

    private void mostrarServicios(List<ServicioHistorial> lista) {
        System.out.println("\nHISTORIAL (" + lista.size() + " servicios)");
        if (lista.isEmpty()) {
            System.out.println("No existen servicios registrados.");
            return;
        }
        for (ServicioHistorial s : lista) {
            System.out.println("  Servicio #" + s.getServicioId()
                    + " | " + s.getTipoMantenimiento()
                    + " | " + s.getEstado());
            System.out.println("    Recibido: " + s.getFechaRecepcion()
                    + " | Entregado: "
                    + (s.getFechaEntrega() == null ? "Aun no" : s.getFechaEntrega()));
            System.out.println("    Problema: " + s.getDescripcionProblema());
            if (s.getOperaciones().isEmpty()) {
                System.out.println("    Sin operaciones registradas.");
            }
            for (OperacionHistorial o : s.getOperaciones()) {
                System.out.println("    - " + o.getNombre()
                        + " | Cobrado: $" + o.getPrecioCobrado().toPlainString()
                        + " | Intervalo: " + (o.getIntervaloMeses() == null
                        ? "No programado" : o.getIntervaloMeses() + " meses"));
            }
        }
    }

    private void mostrarRecordatorios(List<RecordatorioPreventivo> lista) {
        System.out.println("\nRECORDATORIOS (" + lista.size() + ")");
        if (lista.isEmpty()) {
            System.out.println("No hay recordatorios calculados a partir del historial.");
            return;
        }
        for (RecordatorioPreventivo r : lista) {
            System.out.println("  [" + r.getEstado() + "] "
                    + r.getNombreOperacion()
                    + " | Ultima entrega: " + r.getFechaUltimaEntrega()
                    + " | Proxima fecha: " + r.getFechaProxima()
                    + " | Cada " + r.getIntervaloMeses() + " meses");
        }
    }

    private String leer(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().strip();
    }

    private int leerId(String mensaje) {
        while (true) {
            String texto = leer(mensaje);
            try {
                int numero = Integer.parseInt(texto);
                if (numero > 0) return numero;
            } catch (NumberFormatException ignored) {
                // Seguimos solicitando un ID valido.
            }
            System.out.println("Ingresa un entero mayor que cero.");
        }
    }

    private String leerConActual(String nombre, String actual) {
        String texto = leer(nombre + " [" + (actual == null ? "-" : actual)
                + "]: ");
        return texto.isEmpty() ? actual : texto;
    }

    private int leerEnteroConActual(String nombre, int actual) {
        String texto = leer(nombre + " [" + actual + "]: ");
        if (texto.isEmpty()) return actual;
        try {
            int valor = Integer.parseInt(texto);
            if (valor > 0) return valor;
        } catch (NumberFormatException ignored) {
            // Informamos del dato incorrecto.
        }
        throw new IllegalArgumentException("El " + nombre
                + " debe ser un entero positivo.");
    }

    private boolean confirmarCambio() {
        return "SI".equalsIgnoreCase(leer(
                "Escribir SI para guardar el cambio en MySQL: "));
    }

    private boolean confirmarEliminacion() {
        return "ELIMINAR".equals(leer(
                "Escribe ELIMINAR para confirmar: "));
    }
}
