package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.ProveedorController;
import com.carmotors.dao.ProveedorDAO;
import com.carmotors.dao.ProveedorDAOJdbc;
import com.carmotors.dao.factory.ProveedorDAOFactory;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Proveedor;
import com.carmotors.servicio.ProveedorService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pruebas manuales del issue #40. Ejecutar el main desde IntelliJ.
 * No necesita JUnit ni el comando mvn.
 *
 * IMPORTANTE: solamente usa la base de datos carmotors_test.
 * Crea un proveedor temporal y lo elimina al finalizar.
 */
public class ProveedorTest {

    private static int verificaciones = 0;

    public static void main(String[] args) throws SQLException {

        System.out.println("=== PRUEBAS DE PROVEEDORES - ISSUE #40 ===");

        // Comprobacion previa: nunca ejecutar escrituras en otra base.
        try (Connection conexion = conexionDePruebas()) {
            verificar(true, "Conexion confirmada a carmotors_test");
        }

        probarModelo();
        probarFactoryMethod();

        ProveedorService servicio = new ProveedorService(
                new ProveedorDAOFactory(),
                ProveedorTest::conexionDePruebas
        );

        ProveedorController controlador = new ProveedorController(servicio);

        probarConsultasSinCambios(controlador);
        probarCrudYProteccion(controlador);

        System.out.println("\n======================================");
        System.out.println("VERIFICACIONES SUPERADAS: " + verificaciones);
        System.out.println("======================================");
    }

    // =========================================
    // FASE 1: MODELO Y VALIDACIONES
    // =========================================

    private static void probarModelo() {
        System.out.println("\n--- MODELO ---");

        Proveedor conContacto = new Proveedor(
                "  Repuestos de Prueba  ",
                "  Marta Villamizar  ",
                null
        );

        verificar("Repuestos de Prueba".equals(conContacto.getNombre()),
                "Normaliza el nombre con strip()");
        verificar("Marta Villamizar".equals(conContacto.getContacto()),
                "Conserva nombres como contacto, no solo telefonos");
        verificar(conContacto.getEmail() == null,
                "Permite correo ausente si existe contacto");
        verificar(conContacto.getId() == 0,
                "Un proveedor nuevo comienza sin ID asignado");

        Proveedor conCorreo = new Proveedor(
                "Proveedor de prueba", null, "  VENTAS@EMPRESA.COM  "
        );

        verificar(conCorreo.getContacto() == null,
                "Permite contacto ausente si existe correo");
        verificar("ventas@empresa.com".equals(conCorreo.getEmail()),
                "Normaliza el correo electronico");

        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor(null, "Marta", null),
                "Rechaza nombre nulo");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor("   ", "Marta", null),
                "Rechaza nombre en blanco");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor("Proveedor", null, "   "),
                "Rechaza ausencia de ambos medios de contacto");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor("Proveedor", null, "correo-invalido"),
                "Rechaza correo con formato invalido");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor("A".repeat(256), "Marta", null),
                "Rechaza nombres que superan 255 caracteres");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor("Proveedor", "X".repeat(256), null),
                "Rechaza contactos que superan 255 caracteres");
        esperarExcepcion(IllegalArgumentException.class,
                () -> new Proveedor("Proveedor", "Marta", "a".repeat(145) + "@x.com"),
                "Rechaza correos que superan 150 caracteres");

        esperarExcepcion(IllegalArgumentException.class,
                () -> conContacto.setId(null),
                "Rechaza ID nulo");
        esperarExcepcion(IllegalArgumentException.class,
                () -> conContacto.setId(0),
                "Rechaza ID cero");
        esperarExcepcion(IllegalArgumentException.class,
                () -> conContacto.setId(-1),
                "Rechaza ID negativo");

        conContacto.setId(101);
        verificar(conContacto.getId() == 101,
                "Permite asignar un ID valido");
        esperarExcepcion(IllegalStateException.class,
                () -> conContacto.setId(102),
                "Impide modificar un ID ya asignado");
    }

    // =========================================
    // FASE 2: FACTORY METHOD
    // =========================================

    private static void probarFactoryMethod() {
        System.out.println("\n--- FACTORY METHOD ---");

        ProveedorDAOFactory fabrica = new ProveedorDAOFactory();
        ProveedorDAO primero = fabrica.obtenerDAO();
        ProveedorDAO segundo = fabrica.obtenerDAO();

        verificar(primero instanceof ProveedorDAOJdbc,
                "La fabrica entrega una implementacion JDBC");
        verificar(segundo != primero,
                "La fabrica crea instancias independientes");
    }

    // =========================================
    // FASE 3: CONSULTAS Y ENTRADAS INVALIDAS
    // =========================================

    private static void probarConsultasSinCambios(
            ProveedorController controlador) {

        System.out.println("\n--- CONSULTAS ---");

        List<Proveedor> lista = controlador.listarProveedores();
        verificar(lista != null, "Listar proveedores nunca devuelve null");

        verificar(controlador.buscarProveedorPorId(Integer.MAX_VALUE).isEmpty(),
                "Una busqueda de ID inexistente devuelve Optional.empty()");
        verificar(!controlador.existeProveedor(Integer.MAX_VALUE),
                "La consulta de existencia reconoce un ID inexistente");
        verificar(!controlador.existeProveedor(0),
                "La consulta de existencia rechaza ID cero");

        esperarExcepcion(IllegalArgumentException.class,
                () -> controlador.buscarProveedorPorId(0),
                "Buscar por ID cero se rechaza");
        esperarExcepcion(IllegalArgumentException.class,
                () -> controlador.buscarProveedoresPorNombre("   "),
                "La busqueda por nombre exige un criterio");
        esperarExcepcion(IllegalArgumentException.class,
                () -> controlador.buscarProveedoresPorEmail(null),
                "La busqueda por correo exige un criterio");
        esperarExcepcion(IllegalArgumentException.class,
                () -> controlador.eliminarProveedor(-1),
                "La eliminacion rechaza IDs negativos");
    }

    // =========================================
    // FASE 4: CRUD Y CLAVE FORANEA RESTRICT
    // =========================================

    private static void probarCrudYProteccion(
            ProveedorController controlador) throws SQLException {

        System.out.println("\n--- CRUD E INTEGRACION MYSQL ---");

        String codigo = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12);
        String nombre = "TEST_CM_PROVEEDOR_" + codigo;
        String email = "proveedor." + codigo + "@example.com";

        int proveedorId = 0;
        int repuestoId = 0;

        try {
            // CREATE: solo se modifica un registro propio de prueba.
            Proveedor registrado = controlador.registrarProveedor(
                    nombre, "Contacto temporal", email
            );
            proveedorId = registrado.getId();
            final int id = proveedorId;

            verificar(id > 0, "Registra proveedor y recibe ID de MySQL");
            verificar(controlador.existeProveedor(id),
                    "El proveedor registrado existe en MySQL");

            // READ
            Optional<Proveedor> encontrado =
                    controlador.buscarProveedorPorId(id);
            verificar(encontrado.isPresent()
                            && nombre.equals(encontrado.get().getNombre()),
                    "Recupera el proveedor por ID");

            verificar(controlador.buscarProveedoresPorNombre(codigo).stream()
                            .anyMatch(p -> p.getId() == id),
                    "Busca por parte del nombre");

            verificar(controlador.buscarProveedoresPorEmail(email).stream()
                            .anyMatch(p -> p.getId() == id),
                    "Busca por correo exacto");

            verificar(controlador.listarProveedores().stream()
                            .anyMatch(p -> p.getId() == id),
                    "El nuevo proveedor aparece en el listado");

            // UPDATE
            String nombreActualizado = nombre + "_EDITADO";
            verificar(controlador.actualizarProveedor(
                            id, nombreActualizado, "Contacto actualizado", null),
                    "Actualiza los datos del proveedor");

            Proveedor actualizado = controlador.buscarProveedorPorId(id)
                    .orElseThrow();
            verificar(nombreActualizado.equals(actualizado.getNombre())
                            && "Contacto actualizado".equals(actualizado.getContacto())
                            && actualizado.getEmail() == null,
                    "Los nuevos datos persistieron en MySQL");

            verificar(controlador.actualizarProveedor(
                            id, nombreActualizado, "Contacto actualizado", null),
                    "Actualizar sin cambios no se confunde con ID inexistente");

            verificar(controlador.buscarProveedoresPorEmail(email).stream()
                            .noneMatch(p -> p.getId() == id),
                    "El correo anterior deja de aparecer en las busquedas");

            // DELETE PROTEGIDO: relacion creada exclusivamente para la prueba.
            repuestoId = obtenerRepuestoExistente();
            vincularRepuesto(id, repuestoId);

            esperarExcepcion(ValidacionException.class,
                    () -> controlador.eliminarProveedor(id),
                    "MySQL impide eliminar proveedor con repuesto asociado");

            verificar(controlador.existeProveedor(id),
                    "El proveedor sigue existiendo tras la eliminacion rechazada");

            // DELETE EXITOSO: retirar primero nuestra asociacion temporal.
            desvincularRepuesto(id, repuestoId);
            repuestoId = 0;

            verificar(controlador.eliminarProveedor(id),
                    "Elimina un proveedor sin relaciones");
            verificar(!controlador.existeProveedor(id),
                    "El proveedor eliminado ya no existe");
            verificar(!controlador.eliminarProveedor(id),
                    "Eliminar un ID inexistente devuelve false");

        } finally {
            // La limpieza se intenta incluso si una verificacion falla.
            // Solo se eliminan registros creados por esta prueba.
            if (proveedorId > 0) {
                if (repuestoId > 0) {
                    desvincularRepuesto(proveedorId, repuestoId);
                }
                if (controlador.existeProveedor(proveedorId)) {
                    controlador.eliminarProveedor(proveedorId);
                }
            }
        }
    }

    private static int obtenerRepuestoExistente() throws SQLException {
        String sql = "SELECT id FROM repuestos ORDER BY id LIMIT 1";
        try (Connection conexion = conexionDePruebas();
             PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                throw new IllegalStateException(
                        "Se necesita al menos un repuesto en carmotors_test "
                                + "para probar la restriccion de clave foranea"
                );
            }
            return rs.getInt(1);
        }
    }

    private static void vincularRepuesto(int proveedorId, int repuestoId)
            throws SQLException {
        String sql = """
                INSERT INTO proveedores_repuestos
                    (proveedor_id, repuesto_id, costo)
                VALUES (?, ?, ?)
                """;
        try (Connection conexion = conexionDePruebas();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, proveedorId);
            ps.setInt(2, repuestoId);
            ps.setBigDecimal(3, new BigDecimal("1000.00"));
            ps.executeUpdate();
        }
    }

    private static void desvincularRepuesto(int proveedorId, int repuestoId)
            throws SQLException {
        String sql = """
                DELETE FROM proveedores_repuestos
                WHERE proveedor_id = ? AND repuesto_id = ?
                """;
        try (Connection conexion = conexionDePruebas();
             PreparedStatement ps = conexion.prepareStatement(sql)) {
            ps.setInt(1, proveedorId);
            ps.setInt(2, repuestoId);
            ps.executeUpdate();
        }
    }

    // =========================================
    // SEGURIDAD Y HERRAMIENTAS DE VERIFICACION
    // =========================================

    private static Connection conexionDePruebas() {
        Connection conexion =
                ConexionFactory.getInstancia().crearConexion();
        try {
            if (!"carmotors_test".equalsIgnoreCase(conexion.getCatalog())) {
                conexion.close();
                throw new IllegalStateException(
                        "Prueba detenida: solo se permite carmotors_test"
                );
            }
            return conexion;
        } catch (SQLException e) {
            try {
                conexion.close();
            } catch (SQLException errorCierre) {
                e.addSuppressed(errorCierre);
            }
            throw new IllegalStateException(
                    "No se pudo comprobar la base de datos activa", e
            );
        }
    }

    private static void esperarExcepcion(
            Class<? extends Throwable> tipo,
            Runnable accion,
            String descripcion) {
        try {
            accion.run();
        } catch (Throwable error) {
            if (tipo.isInstance(error)) {
                verificar(true, descripcion);
                return;
            }
            throw new AssertionError(
                    "Se esperaba " + tipo.getSimpleName()
                            + " pero ocurrio " + error.getClass().getSimpleName()
                            + ": " + descripcion, error
            );
        }
        throw new AssertionError(
                "No se lanzo " + tipo.getSimpleName()
                        + ": " + descripcion
        );
    }

    private static void verificar(boolean condicion, String descripcion) {
        if (!condicion) {
            throw new AssertionError("FALLO: " + descripcion);
        }
        verificaciones++;
        System.out.println("OK " + verificaciones + " - " + descripcion);
    }
}
