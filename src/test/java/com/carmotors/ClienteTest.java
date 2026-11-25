package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.ClienteController;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.ClienteDAOJdbc;
import com.carmotors.dao.ClienteVehiculosDAO;
import com.carmotors.dao.ClienteVehiculosDAOJdbc;
import com.carmotors.excepcion.AccesoDatosException;
import com.carmotors.excepcion.ClienteConVehiculosException;
import com.carmotors.excepcion.ClienteDuplicadoException;
import com.carmotors.excepcion.ClienteNoEncontradoException;
import com.carmotors.modelo.Cliente;
import com.carmotors.servicio.ClienteService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ClienteTest {

    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        String sufijo = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 10);

        // Protección: nunca modificar una base distinta a la de pruebas.
        try (Connection conexion = ConexionFactory.getInstancia().crearConexion()) {
            verificar("carmotors_test".equalsIgnoreCase(conexion.getCatalog()),
                    "Conexión a carmotors_test");
        }

        probarModelo();
        probarDAO(sufijo);
        probarService(sufijo);
        probarController(sufijo);

        System.out.println("\n========================================");
        System.out.println("TODAS LAS PRUEBAS SUPERADAS: " + verificaciones);
        System.out.println("========================================");
    }

    // ==================================================
    // 1. MODELO: NO UTILIZA BASE DE DATOS
    // ==================================================
    private static void probarModelo() {
        titulo("MODELO CLIENTE");

        Cliente cliente = new Cliente(
                "  Ana Lopez  ", "  12-34  ", "  3001234567  ",
                "  ANA@EXAMPLE.COM  "
        );

        verificar(cliente.getId() == 0, "Cliente nuevo sin ID de MySQL");
        verificar("ANA LOPEZ".equals(cliente.getNombre()), "Nombre normalizado");
        verificar("12-34".equals(cliente.getDocumento()), "Documento recortado; acepta guion");
        verificar("3001234567".equals(cliente.getTelefono()), "Teléfono recortado");
        verificar("ana@example.com".equals(cliente.getEmail()), "Email normalizado");

        verificar(new Cliente("Ana", "A1", "123", null).getEmail() == null,
                "Email null permitido");
        verificar(new Cliente("Ana", "A2", "123", "   ").getEmail() == null,
                "Email en blanco se convierte en null");

        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("   ", "A3", "123", null), "Nombre vacío rechazado");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("Ana", "   ", "123", null), "Documento vacío rechazado");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("Ana", "A4", "   ", null), "Teléfono vacío rechazado");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("N".repeat(256), "A5", "123", null),
                "Nombre mayor a 255 rechazado");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("Ana", "D".repeat(21), "123", null),
                "Documento mayor a 20 rechazado");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("Ana", "A6", "3".repeat(21), null),
                "Teléfono mayor a 20 rechazado");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Cliente("Ana", "A7", "123", "e".repeat(101)),
                "Email mayor a 100 rechazado");

        esperarExcepcion(IllegalArgumentException.class,
                () -> cliente.setId(0), "ID cero rechazado");
        cliente.setId(55);
        verificar(cliente.getId() == 55, "ID válido asignado");
        esperarExcepcion(IllegalStateException.class,
                () -> cliente.setId(56), "No permite asignar ID dos veces");
    }

    // ==================================================
    // 2. DAO: UNA TRANSACCIÓN QUE SIEMPRE SE REVIERTE
    // ==================================================
    private static void probarDAO(String sufijo) throws Exception {
        titulo("CLIENTE DAO JDBC");
        ClienteDAO dao = new ClienteDAOJdbc();
        String doc = "D" + sufijo;
        String docSinEmail = "E" + sufijo;
        String email = "dao." + sufijo + "@example.com";

        try (Connection conexion = ConexionFactory.getInstancia().crearConexion()) {
            conexion.setAutoCommit(false);
            try {
                Cliente cliente = new Cliente("Cliente DAO", doc, "3001001001", email);
                dao.insertar(conexion, cliente);
                int id = cliente.getId();
                verificar(id > 0, "DAO inserta y obtiene ID");
                verificar(dao.buscarPorId(conexion, id).isPresent(), "DAO busca por ID");
                verificar(dao.buscarPorDocumento(conexion, doc).isPresent(),
                        "DAO busca por documento");
                verificar(dao.buscarPorEmail(conexion, email).isPresent(),
                        "DAO busca por email");
                verificar(dao.listar(conexion).stream().anyMatch(c -> c.getId() == id),
                        "DAO incluye el cliente en el listado");
                verificar(dao.buscarPorId(conexion, Integer.MAX_VALUE).isEmpty(),
                        "DAO devuelve Optional vacío para ID inexistente");

                AccesoDatosException duplicadoDoc = esperarExcepcion(
                        AccesoDatosException.class,
                        () -> dao.insertar(conexion, new Cliente(
                                "Duplicado", doc, "3001001002", null)),
                        "DAO rechaza documento UNIQUE duplicado");
                verificar(esErrorMysql(duplicadoDoc, 1062),
                        "Documento duplicado identificado con error MySQL 1062");

                AccesoDatosException duplicadoEmail = esperarExcepcion(
                        AccesoDatosException.class,
                        () -> dao.insertar(conexion, new Cliente(
                                "Duplicado Email", "F" + sufijo, "3001001003", email)),
                        "DAO rechaza email UNIQUE duplicado");
                verificar(esErrorMysql(duplicadoEmail, 1062),
                        "Email duplicado identificado con error MySQL 1062");

                Cliente sinEmail = new Cliente(
                        "Sin Email DAO", docSinEmail, "3001001004", null);
                dao.insertar(conexion, sinEmail);
                verificar(dao.buscarPorId(conexion, sinEmail.getId())
                                .orElseThrow().getEmail() == null,
                        "DAO guarda email como NULL");

                Cliente actualizado = new Cliente(
                        "DAO Actualizado", doc, "3011111111", email);
                actualizado.setId(id);
                verificar(dao.actualizar(conexion, actualizado), "DAO actualiza cliente");
                verificar("3011111111".equals(dao.buscarPorId(conexion, id)
                                .orElseThrow().getTelefono()),
                        "DAO persiste los datos actualizados en la transacción");
                verificar(dao.actualizar(conexion, actualizado),
                        "DAO acepta UPDATE sin cambios para ID existente");
                verificar(!dao.actualizar(conexion, clienteConId(
                                "Inexistente", "Z" + sufijo, "300", null,
                                Integer.MAX_VALUE)),
                        "DAO informa que no actualizó ID inexistente");

                verificar(dao.eliminar(conexion, id), "DAO elimina cliente sin vehículos");
                verificar(dao.buscarPorId(conexion, id).isEmpty(),
                        "DAO confirma ausencia después de DELETE");
                verificar(!dao.eliminar(conexion, id),
                        "DAO devuelve false al eliminar ID inexistente");
            } finally {
                conexion.rollback();
                System.out.println("[INFO] Cambios DAO revertidos con rollback");
            }
        }

        // La reversión debe ser visible desde una conexión nueva.
        try (Connection conexion = ConexionFactory.getInstancia().crearConexion()) {
            verificar(dao.buscarPorDocumento(conexion, docSinEmail).isEmpty(),
                    "Rollback DAO no deja registros temporales");
        }
    }

    // ==================================================
    // 3. SERVICE: REGLAS DE NEGOCIO Y TRANSACCIONES
    // ==================================================
    private static void probarService(String sufijo) throws Exception {
        titulo("CLIENTE SERVICE");
        ClienteDAO dao = new ClienteDAOJdbc();
        ClienteVehiculosDAO vehiculosDAO = new ClienteVehiculosDAOJdbc();
        ClienteService servicio = new ClienteService(dao, vehiculosDAO);

        String doc1 = "S" + sufijo;
        String doc2 = "T" + sufijo;
        String docNuevo = "U" + sufijo;
        String correo1 = "servicio." + sufijo + "@example.com";
        String correoNuevo = "cambio." + sufijo + "@example.com";

        try {
            Cliente uno = new Cliente("Cliente Servicio", doc1, "3002002001", correo1);
            servicio.registrar(uno);
            int id1 = uno.getId();
            verificar(id1 > 0, "Service registra cliente");

            try (Connection conexion = ConexionFactory.getInstancia().crearConexion()) {
                verificar(dao.buscarPorId(conexion, id1).isPresent(),
                        "Service confirma registro con commit");
            }
            verificar(servicio.obtenerPorId(id1).getId() == id1,
                    "Service obtiene cliente por ID");

            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> servicio.registrar(new Cliente(
                            "Duplicado", doc1, "3002002002", null)),
                    "Service bloquea documento duplicado");
            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> servicio.registrar(new Cliente(
                            "Duplicado Email", "V" + sufijo, "3002002003", correo1)),
                    "Service bloquea email duplicado");

            Cliente dos = new Cliente("Sin Correo", doc2, "3002002004", null);
            servicio.registrar(dos);
            int id2 = dos.getId();
            verificar(id2 > 0 && servicio.obtenerPorId(id2).getEmail() == null,
                    "Service registra email opcional");

            verificar(servicio.buscarPorDocumento(doc1)
                            .map(c -> c.getId() == id1).orElse(false),
                    "Service busca por documento");
            verificar(servicio.buscarPorDocumento("W" + sufijo).isEmpty(),
                    "Service devuelve vacío para documento inexistente");
            verificar(servicio.buscarPorEmail(
                                    "  " + correo1.toUpperCase(Locale.ROOT) + "  ")
                            .map(c -> c.getId() == id1).orElse(false),
                    "Service normaliza email al buscar");
            verificar(servicio.buscarPorEmail("no." + sufijo + "@example.com").isEmpty(),
                    "Service devuelve vacío para email inexistente");
            esperarExcepcion(IllegalArgumentException.class,
                    () -> servicio.buscarPorEmail("   "),
                    "Service rechaza búsqueda con email vacío");
            esperarExcepcion(IllegalArgumentException.class,
                    () -> servicio.obtenerPorId(0), "Service rechaza ID cero");
            esperarExcepcion(ClienteNoEncontradoException.class,
                    () -> servicio.obtenerPorId(Integer.MAX_VALUE),
                    "Service rechaza ID inexistente");
            verificar(servicio.listar().stream().anyMatch(c -> c.getId() == id1)
                            && servicio.listar().stream().anyMatch(c -> c.getId() == id2),
                    "Service lista ambos clientes temporales");

            Cliente mismosDatos = clienteConId(
                    "Cliente Servicio", doc1, "3002002001", correo1, id1);
            verificar(servicio.actualizar(mismosDatos).getId() == id1,
                    "Service permite mantener documento y email propios");

            Cliente modificado = clienteConId(
                    "Cliente Actualizado", docNuevo, "3012002001", correoNuevo, id1);
            servicio.actualizar(modificado);
            verificar("3012002001".equals(servicio.obtenerPorId(id1).getTelefono())
                            && docNuevo.equals(servicio.obtenerPorId(id1).getDocumento()),
                    "Service actualiza y persiste datos");
            verificar(servicio.buscarPorDocumento(doc1).isEmpty()
                            && servicio.buscarPorEmail(correo1).isEmpty(),
                    "Service libera documento y email anteriores");

            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> servicio.actualizar(clienteConId(
                            "Conflicto", doc2, "300", correoNuevo, id1)),
                    "Service impide utilizar documento de otro cliente");
            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> servicio.actualizar(clienteConId(
                            "Conflicto", doc2, "300", correoNuevo, id2)),
                    "Service impide utilizar email de otro cliente");
            esperarExcepcion(ClienteNoEncontradoException.class,
                    () -> servicio.actualizar(clienteConId(
                            "No Existe", "X" + sufijo, "300", null,
                            Integer.MAX_VALUE)),
                    "Service impide actualizar ID inexistente");

            servicio.actualizar(clienteConId(
                    "Sin Correo Editado", doc2, "3012002004", null, id2));
            verificar(servicio.obtenerPorId(id2).getEmail() == null,
                    "Service permite actualizar cliente sin email");
            servicio.actualizar(clienteConId(
                    "Cliente Actualizado", docNuevo, "3012002001", null, id1));
            verificar(servicio.obtenerPorId(id1).getEmail() == null,
                    "Service permite quitar email con UPDATE");

            // Doble de prueba: simula un vehículo para nuestro cliente temporal.
            ClienteVehiculosDAO vehiculosSimulados =
                    (conexion, clienteId) -> clienteId == id2;
            ClienteService servicioConDoble =
                    new ClienteService(dao, vehiculosSimulados);
            esperarExcepcion(ClienteConVehiculosException.class,
                    () -> servicioConDoble.eliminar(id2),
                    "Service bloquea eliminación usando doble de vehículos");
            verificar(servicio.obtenerPorId(id2).getId() == id2,
                    "El doble no permitió borrar al cliente temporal");

            // Si hay datos precargados, también comprobamos la relación real.
            Integer protegido = buscarClienteConVehiculos();
            if (protegido != null) {
                esperarExcepcion(ClienteConVehiculosException.class,
                        () -> servicio.eliminar(protegido),
                        "Service bloquea eliminación con vehículos reales");
                verificar(servicio.obtenerPorId(protegido).getId() == protegido,
                        "Cliente real con vehículos permanece en MySQL");
            } else {
                System.out.println("[INFO] No hay vehículo precargado; "
                        + "se verificó el bloqueo con el doble local");
            }

            servicio.eliminar(id2);
            esperarExcepcion(ClienteNoEncontradoException.class,
                    () -> servicio.obtenerPorId(id2),
                    "Service confirma eliminación del segundo cliente");
            esperarExcepcion(ClienteNoEncontradoException.class,
                    () -> servicio.eliminar(id2),
                    "Service rechaza eliminación repetida");
            esperarExcepcion(IllegalArgumentException.class,
                    () -> servicio.eliminar(0),
                    "Service rechaza eliminación con ID inválido");

            servicio.eliminar(id1);
            try (Connection conexion = ConexionFactory.getInstancia().crearConexion()) {
                verificar(dao.buscarPorId(conexion, id1).isEmpty(),
                        "Service confirma eliminación con commit");
            }
        } finally {
            limpiarClientes(doc1, doc2, docNuevo);
        }
    }

    // ==================================================
    // 4. CONTROLLER: DELEGACIÓN DESDE LA CAPA SUPERIOR
    // ==================================================
    private static void probarController(String sufijo) throws Exception {
        titulo("CLIENTE CONTROLLER");
        ClienteDAO dao = new ClienteDAOJdbc();
        ClienteVehiculosDAO vehiculosDAO = new ClienteVehiculosDAOJdbc();
        ClienteController controller = new ClienteController(
                new ClienteService(dao, vehiculosDAO));

        String doc1 = "C" + sufijo;
        String doc2 = "G" + sufijo;
        String docNuevo = "H" + sufijo;
        String email = "controller." + sufijo + "@example.com";
        String emailNuevo = "controller2." + sufijo + "@example.com";

        try {
            Cliente uno = controller.registrarCliente(
                    "Cliente Controller", doc1, "3003003001", email);
            int id1 = uno.getId();
            verificar(id1 > 0, "Controller registra cliente");
            verificar("CLIENTE CONTROLLER".equals(uno.getNombre()),
                    "Controller devuelve datos normalizados");

            Cliente dos = controller.registrarCliente(
                    "Segundo Cliente", doc2, "3003003002", null);
            int id2 = dos.getId();
            verificar(id2 > 0 && dos.getEmail() == null,
                    "Controller registra sin email");
            esperarExcepcion(IllegalArgumentException.class,
                    () -> controller.registrarCliente(
                            "   ", "J" + sufijo, "300", null),
                    "Controller rechaza nombre vacío");
            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> controller.registrarCliente(
                            "Duplicado", doc1, "300", null),
                    "Controller rechaza documento duplicado");
            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> controller.registrarCliente(
                            "Duplicado Email", "K" + sufijo, "300", email),
                    "Controller rechaza email duplicado");

            verificar(controller.obtenerClientePorId(id1).getId() == id1,
                    "Controller obtiene por ID");
            verificar(controller.buscarClientePorDocumento(doc1)
                            .map(c -> c.getId() == id1).orElse(false),
                    "Controller busca por documento");
            verificar(controller.buscarClientePorEmail(email)
                            .map(c -> c.getId() == id1).orElse(false),
                    "Controller busca por email");
            verificar(controller.listarClientes().stream().anyMatch(c -> c.getId() == id1)
                            && controller.listarClientes().stream()
                            .anyMatch(c -> c.getId() == id2),
                    "Controller lista clientes");
            esperarExcepcion(ClienteNoEncontradoException.class,
                    () -> controller.obtenerClientePorId(Integer.MAX_VALUE),
                    "Controller propaga cliente no encontrado");

            Cliente modificado = controller.actualizarCliente(
                    id1, "Controller Actualizado", docNuevo, "3013003001", emailNuevo);
            verificar(modificado.getId() == id1,
                    "Controller devuelve el cliente actualizado");
            verificar(docNuevo.equals(controller.obtenerClientePorId(id1).getDocumento())
                            && emailNuevo.equals(controller.obtenerClientePorId(id1).getEmail()),
                    "Controller persiste actualización");
            esperarExcepcion(ClienteDuplicadoException.class,
                    () -> controller.actualizarCliente(
                            id2, "Conflicto", docNuevo, "300", null),
                    "Controller rechaza documento repetido al actualizar");
            esperarExcepcion(IllegalArgumentException.class,
                    () -> controller.actualizarCliente(
                            0, "ID Inválido", "L" + sufijo, "300", null),
                    "Controller rechaza actualización con ID cero");

            controller.eliminarCliente(id2);
            verificar(controller.buscarClientePorDocumento(doc2).isEmpty(),
                    "Controller elimina el segundo cliente");
            esperarExcepcion(ClienteNoEncontradoException.class,
                    () -> controller.eliminarCliente(id2),
                    "Controller rechaza eliminación repetida");
            controller.eliminarCliente(id1);
            verificar(controller.buscarClientePorDocumento(docNuevo).isEmpty(),
                    "Controller elimina el primer cliente");
        } finally {
            limpiarClientes(doc1, doc2, docNuevo);
        }
    }

    // ==================================================
    // AUXILIARES
    // ==================================================
    private static Cliente clienteConId(String nombre, String documento,
                                        String telefono, String email, int id) {
        Cliente cliente = new Cliente(nombre, documento, telefono, email);
        cliente.setId(id);
        return cliente;
    }

    private static Integer buscarClienteConVehiculos() throws SQLException {
        String sql = "SELECT v.cliente_id "
                + "FROM vehiculos v INNER JOIN clientes c ON c.id = v.cliente_id "
                + "ORDER BY v.cliente_id LIMIT 1";
        try (Connection conexion = ConexionFactory.getInstancia().crearConexion();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    private static void limpiarClientes(String doc1, String doc2, String doc3)
            throws SQLException {
        String sql = "DELETE FROM clientes WHERE documento_identidad IN (?, ?, ?)";
        try (Connection conexion = ConexionFactory.getInstancia().crearConexion();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setString(1, doc1);
            ps.setString(2, doc2);
            ps.setString(3, doc3);
            int eliminados = ps.executeUpdate();
            System.out.println("[INFO] Limpieza temporal: " + eliminados
                    + " registro(s) restante(s) eliminado(s)");
        }
    }

    private static boolean esErrorMysql(AccesoDatosException e, int codigo) {
        return e.getCause() instanceof SQLException sql
                && sql.getErrorCode() == codigo;
    }

    private static void titulo(String seccion) {
        System.out.println("\n========== " + seccion + " ==========");
    }

    private static void verificar(boolean condicion, String descripcion) {
        if (!condicion) {
            throw new IllegalStateException("[FALLO] " + descripcion);
        }
        verificaciones++;
        System.out.println("[OK] " + descripcion);
    }

    private static <T extends RuntimeException> T esperarExcepcion(
            Class<T> tipo, Runnable accion, String descripcion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            if (tipo.isInstance(e)) {
                verificaciones++;
                System.out.println("[OK] " + descripcion);
                return tipo.cast(e);
            }
            throw e;
        }
        throw new IllegalStateException(
                "[FALLO] No se lanzó " + tipo.getSimpleName()
                        + ": " + descripcion);
    }
}
