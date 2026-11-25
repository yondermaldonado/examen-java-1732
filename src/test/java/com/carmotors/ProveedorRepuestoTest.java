package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.ProveedorRepuestoController;
import com.carmotors.dao.ProveedorRepuestoDAO;
import com.carmotors.dao.ProveedorRepuestoDAOJdbc;
import com.carmotors.dao.RepuestoDAOJdbc;
import com.carmotors.dao.factory.ProveedorRepuestoDAOFactory;
import com.carmotors.excepcion.AccesoDatosException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.ProveedorRepuesto;
import com.carmotors.modelo.Repuesto;
import com.carmotors.servicio.ProveedorConsulta;
import com.carmotors.servicio.ProveedorRepuestoService;
import com.carmotors.servicio.RepuestoConsulta;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Issue #41: pruebas sin JUnit. Ejecutar main() desde IntelliJ con Java 17.
 * SOLO usa la base carmotors_test. Crea y elimina datos temporales propios.
 * No prueba el historial de CAMBIOS de costos: ese registro aún no es requisito.
 */
public class ProveedorRepuestoTest {

    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("=== CARMOTORS | ISSUE #41 | PRUEBAS ===");

        probarModelo();
        probarFactory();

        try (Connection conexion = abrirConexion()) {
            comprobar(true, "Conexión segura a carmotors_test");
        }

        Datos datos = new Datos();
        try {
            prepararDatos(datos);
            probarPersistenciaYServicio(datos);
            System.out.println("\nVERIFICACIONES SUPERADAS: " + verificaciones);
        } finally {
            limpiarDatos(datos);
            System.out.println("Datos temporales de pruebas eliminados.");
        }
    }

    private static void probarModelo() {
        System.out.println("\n--- MODELO ---");
        ProveedorRepuesto original = new ProveedorRepuesto(1, 5, dinero("32000"));
        comprobar(original.getProveedorId() == 1, "Conserva ID del proveedor");
        comprobar(original.getRepuestoId() == 5, "Conserva ID del repuesto");
        comprobar(original.getCosto().equals(dinero("32000.00")), "Normaliza costo a dos decimales");

        ProveedorRepuesto nuevo = original.conCosto(dinero("35000.00"));
        comprobar(original.getCosto().compareTo(dinero("32000")) == 0,
                "conCosto no modifica el objeto original");
        comprobar(nuevo.getCosto().compareTo(dinero("35000")) == 0,
                "conCosto crea una nueva instancia con otro valor");
        comprobar(nuevo != original && nuevo.getProveedorId() == 1 && nuevo.getRepuestoId() == 5,
                "conCosto mantiene la identidad compuesta");

        comprobar(new ProveedorRepuesto(1, 5, BigDecimal.ZERO)
                .getCosto().compareTo(BigDecimal.ZERO) == 0, "Permite costo cero");
        comprobar(new ProveedorRepuesto(1, 5, dinero("99999999.99"))
                        .getCosto().compareTo(dinero("99999999.99")) == 0,
                "Permite el valor máximo DECIMAL(10,2)");
        comprobar(new ProveedorRepuesto(1, 5, dinero("5.000"))
                        .getCosto().compareTo(dinero("5.00")) == 0,
                "Acepta ceros decimales sobrantes sin perder precisión");

        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(0, 5, dinero("1")), "Rechaza proveedor cero");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(-1, 5, dinero("1")), "Rechaza proveedor negativo");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(1, 0, dinero("1")), "Rechaza repuesto cero");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(1, -1, dinero("1")), "Rechaza repuesto negativo");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(1, 5, null), "Rechaza costo nulo");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(1, 5, dinero("-0.01")), "Rechaza costo negativo");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(1, 5, dinero("1.234")), "Rechaza tres decimales significativos");
        esperar(IllegalArgumentException.class,
                () -> new ProveedorRepuesto(1, 5, dinero("100000000.00")), "Rechaza valor fuera de DECIMAL(10,2)");
    }

    private static void probarFactory() {
        System.out.println("\n--- FACTORY METHOD ---");
        ProveedorRepuestoDAOFactory fabrica = new ProveedorRepuestoDAOFactory();
        ProveedorRepuestoDAO primero = fabrica.obtenerDAO();
        ProveedorRepuestoDAO segundo = fabrica.obtenerDAO();
        comprobar(primero instanceof ProveedorRepuestoDAOJdbc, "Factory devuelve un DAO JDBC");
        comprobar(primero != segundo, "Factory crea instancias independientes");
    }

    private static void probarPersistenciaYServicio(Datos d) throws SQLException {
        System.out.println("\n--- DAO + SERVICE + CONTROLLER ---");
        Supplier<Connection> conexiones = ProveedorRepuestoTest::abrirConexion;
        ProveedorRepuestoDAO dao = new ProveedorRepuestoDAOJdbc();

        // Puertos reales de solo lectura: no se utiliza el inventario como DAO de escritura.
        ProveedorConsulta proveedores = id -> existe("proveedores", id);
        RepuestoConsulta repuestos = id -> existe("repuestos", id);
        ProveedorRepuestoService servicio = new ProveedorRepuestoService(
                new ProveedorRepuestoDAOFactory(), proveedores, repuestos, conexiones);
        ProveedorRepuestoController controlador = new ProveedorRepuestoController(servicio);

        comprobar(controlador.buscarAsociacion(d.proveedor1, d.repuesto1).isEmpty(),
                "Asociación temporal inicialmente inexistente");
        comprobar(!servicio.actualizarCosto(d.proveedor1, d.repuesto1, dinero("35000")),
                "Actualizar asociación inexistente devuelve false");

        ProveedorRepuesto creada = controlador.registrarAsociacion(
                d.proveedor1, d.repuesto1, " 32000.00 ");
        comprobar(creada.getCosto().compareTo(dinero("32000")) == 0,
                "Controller convierte String a BigDecimal");

        servicio.registrar(d.proveedor1, d.repuesto2, BigDecimal.ZERO);
        servicio.registrar(new ProveedorRepuesto(d.proveedor2, d.repuesto1, dinero("33000")));
        comprobar(controlador.listarPorProveedor(d.proveedor1).size() == 2,
                "Un proveedor puede vender varios repuestos");
        comprobar(controlador.listarPorRepuesto(d.repuesto1).size() == 2,
                "Un repuesto puede tener varios proveedores");

        try (Connection con = abrirConexion()) {
            Optional<ProveedorRepuesto> encontrada = dao.buscar(con, d.proveedor1, d.repuesto1);
            comprobar(encontrada.isPresent(), "DAO busca por la clave compuesta");
            comprobar(encontrada.orElseThrow().getCosto().compareTo(dinero("32000")) == 0,
                    "DAO recupera costo exacto de MySQL");
            comprobar(dao.listarPorProveedor(con, d.proveedor2).size() == 1,
                    "DAO filtra por proveedor");
            comprobar(dao.listarPorRepuesto(con, d.repuesto2).size() == 1,
                    "DAO filtra por repuesto");
        }

        esperar(ValidacionException.class,
                () -> controlador.registrarAsociacion(d.proveedor1, d.repuesto1, "32000"),
                "La PK compuesta impide asociaciones duplicadas");
        comprobar(contarAsociaciones(d.proveedor1, d.repuesto1) == 1,
                "El duplicado fallido no genera nuevas filas");
        esperar(ValidacionException.class,
                () -> controlador.registrarAsociacion(d.proveedor1, d.repuesto1, "abc"),
                "Controller rechaza texto no numérico");
        esperar(ValidacionException.class,
                () -> controlador.registrarAsociacion(d.proveedor1, d.repuesto1, "  "),
                "Controller rechaza costo vacío");
        esperar(IllegalArgumentException.class,
                () -> controlador.registrarAsociacion(d.proveedor1, d.repuesto1, "-4"),
                "Modelo rechaza costo negativo desde Controller");
        esperar(ValidacionException.class,
                () -> servicio.registrar(Integer.MAX_VALUE, d.repuesto1, dinero("2")),
                "Service rechaza proveedor inexistente");
        esperar(ValidacionException.class,
                () -> servicio.registrar(d.proveedor1, Integer.MAX_VALUE, dinero("2")),
                "Service rechaza repuesto inexistente");
        esperar(ValidacionException.class,
                () -> servicio.registrar((ProveedorRepuesto) null),
                "Service rechaza asociación nula");
        esperar(ValidacionException.class,
                () -> servicio.buscar(0, d.repuesto1), "Service valida ID de consulta");

        // Error de FK incluso si un consumidor del DAO evita los puertos del servicio.
        try (Connection con = abrirConexion()) {
            esperar(ValidacionException.class,
                    () -> dao.insertar(con, new ProveedorRepuesto(
                            Integer.MAX_VALUE, d.repuesto1, dinero("10"))),
                    "MySQL impide insertar un proveedor inexistente");
        }

        List<String> lotesAntes = filas("SELECT lote_id, repuesto_id, costo_unitario "
                + "FROM lotes_repuestos ORDER BY lote_id, repuesto_id");
        List<String> serviciosAntes = filas("SELECT servicio_id, repuesto_id, precio_unitario_venta "
                + "FROM servicios_repuestos ORDER BY servicio_id, repuesto_id");
        List<String> facturasAntes = filas("SELECT id, total_repuestos, total_mano_obra, impuestos, total_final "
                + "FROM facturas ORDER BY id");
        List<String> ventaAntes = filas("SELECT id, precio_venta FROM repuestos ORDER BY id");

        comprobar(controlador.actualizarCosto(d.proveedor1, d.repuesto1, "34000.00"),
                "Controller actualiza el costo ofertado");
        comprobar(controlador.actualizarCosto(d.proveedor1, d.repuesto1, "34000.00"),
                "Actualizar el mismo costo se considera éxito");
        comprobar(controlador.actualizarCosto(d.proveedor1, d.repuesto2, "99999999.99"),
                "MySQL acepta el máximo permitido");
        comprobar(controlador.buscarAsociacion(d.proveedor1, d.repuesto1)
                        .orElseThrow().getCosto().compareTo(dinero("34000")) == 0,
                "El costo actualizado persiste entre conexiones");
        comprobar(contarAsociaciones(d.proveedor1, d.repuesto1) == 1,
                "Actualizar costo idéntico no duplica la asociación");
        comprobar(controlador.buscarAsociacion(d.proveedor2, d.repuesto1)
                        .orElseThrow().getCosto().compareTo(dinero("33000")) == 0,
                "Actualizar un proveedor no altera el costo de otro");

        comprobar(lotesAntes.equals(filas("SELECT lote_id, repuesto_id, costo_unitario "
                        + "FROM lotes_repuestos ORDER BY lote_id, repuesto_id")),
                "No modifica costos históricos de adquisición por lote");
        comprobar(serviciosAntes.equals(filas("SELECT servicio_id, repuesto_id, precio_unitario_venta "
                        + "FROM servicios_repuestos ORDER BY servicio_id, repuesto_id")),
                "No modifica precios históricos de servicios");
        comprobar(facturasAntes.equals(filas("SELECT id, total_repuestos, total_mano_obra, impuestos, total_final "
                        + "FROM facturas ORDER BY id")),
                "No modifica importes facturados");
        comprobar(ventaAntes.equals(filas("SELECT id, precio_venta FROM repuestos ORDER BY id")),
                "No confunde costo de proveedor con precio de venta");

        probarRollbackDeDao(dao, d);
        probarRollbackDeService(dao, proveedores, repuestos, conexiones, d);
        probarLecturaDesdeInventario(d);
        probarActualizacionDesdeInventario(d);
    }

    private static void probarRollbackDeDao(ProveedorRepuestoDAO dao, Datos d) throws SQLException {
        System.out.println("\n--- ROLLBACK DAO ---");
        try (Connection con = abrirConexion()) {
            con.setAutoCommit(false);
            try {
                dao.insertar(con, new ProveedorRepuesto(d.proveedor2, d.repuesto2, dinero("12000")));
                comprobar(dao.buscar(con, d.proveedor2, d.repuesto2).isPresent(),
                        "DAO ve su inserción dentro de la transacción");
            } finally {
                con.rollback();
            }
        }
        comprobar(contarAsociaciones(d.proveedor2, d.repuesto2) == 0,
                "DAO no hace commit: el rollback revierte la inserción");
    }

    private static void probarRollbackDeService(
            ProveedorRepuestoDAO dao,
            ProveedorConsulta proveedores,
            RepuestoConsulta repuestos,
            Supplier<Connection> conexiones,
            Datos d) {

        System.out.println("\n--- ROLLBACK SERVICE ---");
        ProveedorRepuestoDAO conFalloSimulado = new ProveedorRepuestoDAO() {
            @Override
            public void insertar(Connection con, ProveedorRepuesto a) {
                dao.insertar(con, a);
                throw new IllegalStateException("Fallo simulado DESPUÉS del INSERT");
            }
            @Override
            public Optional<ProveedorRepuesto> buscar(Connection con, int p, int r) {
                return dao.buscar(con, p, r);
            }
            @Override
            public List<ProveedorRepuesto> listarPorProveedor(Connection con, int p) {
                return dao.listarPorProveedor(con, p);
            }
            @Override
            public List<ProveedorRepuesto> listarPorRepuesto(Connection con, int r) {
                return dao.listarPorRepuesto(con, r);
            }
            @Override
            public boolean actualizarCosto(Connection con, ProveedorRepuesto a) {
                return dao.actualizarCosto(con, a);
            }
        };

        ProveedorRepuestoService servicioConFallo = new ProveedorRepuestoService(
                conFalloSimulado, proveedores, repuestos, conexiones);
        esperar(IllegalStateException.class,
                () -> servicioConFallo.registrar(d.proveedor2, d.repuesto2, dinero("12000")),
                "Service propaga el fallo después del INSERT");
        comprobar(contarAsociaciones(d.proveedor2, d.repuesto2) == 0,
                "Service revierte la transacción cuando el DAO falla");
    }

    private static void probarLecturaDesdeInventario(Datos d) throws SQLException {
        System.out.println("\n--- REGRESION INVENTARIO ---");
        try (Connection con = abrirConexion()) {
            List<Repuesto> inventario = new RepuestoDAOJdbc()
                    .listarPorProveedor(con, d.proveedor1);
            comprobar(inventario.size() == 2,
                    "Inventario existente sigue listando repuestos por proveedor");
            comprobar(inventario.stream().anyMatch(r -> r.getId() == d.repuesto1
                            && r.getPrecioCompra().compareTo(dinero("34000")) == 0),
                    "Inventario lee el costo actualizado del catálogo");
        }
    }

    /**
     * Regresión de la segunda ruta que escribe en proveedores_repuestos.
     * La operación se ejecuta sobre un repuesto temporal y se revierte siempre.
     * Esta prueba puede revelar el caso UPDATE de costo idéntico -> INSERT duplicado.
     */
    private static void probarActualizacionDesdeInventario(Datos d) throws SQLException {
        try (Connection con = abrirConexion()) {
            con.setAutoCommit(false);
            try {
                Repuesto repuesto = new Repuesto();
                repuesto.setId(d.repuesto1);
                repuesto.setNombre(d.nombreRepuesto1);
                repuesto.setTipoId(d.tipo);
                repuesto.setMarcaId(d.marca);
                repuesto.setProveedorId(d.proveedor1);
                repuesto.setPrecioCompra(dinero("34000.00"));
                repuesto.setPrecioVenta(dinero("55000.00"));
                repuesto.setStockMinimo(0);

                comprobar(new RepuestoDAOJdbc().actualizar(con, repuesto),
                        "Inventario permite actualizar sin cambiar los datos del repuesto ni su costo");
            } finally {
                con.rollback();
            }
        }
        comprobar(contarAsociaciones(d.proveedor1, d.repuesto1) == 1,
                "Actualización de inventario no deja vínculos duplicados");
    }

    private static void prepararDatos(Datos d) throws SQLException {
        System.out.println("\n--- CREANDO FIXTURES TEMPORALES ---");
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        try (Connection con = abrirConexion()) {
            d.tipo = primerId(con, "repuesto_tipos");
            d.marca = primerId(con, "marcas_repuesto");
            d.nombreRepuesto1 = "TEST41 Repuesto A " + sufijo;
            d.proveedor1 = insertarProveedor(con, "TEST41 Proveedor A " + sufijo);
            d.proveedor2 = insertarProveedor(con, "TEST41 Proveedor B " + sufijo);
            d.repuesto1 = insertarRepuesto(con, d.nombreRepuesto1, d.tipo, d.marca);
            d.repuesto2 = insertarRepuesto(con, "TEST41 Repuesto B " + sufijo, d.tipo, d.marca);
        }
    }

    private static int insertarProveedor(Connection con, String nombre) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO proveedores (nombre, contacto, email) VALUES (?, ?, NULL)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, "Contacto de pruebas");
            return insertarYObtenerId(ps);
        }
    }

    private static int insertarRepuesto(Connection con, String nombre, int tipo, int marca)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO repuestos (nombre, repuesto_tipo_id, marca_id, precio_venta, stock_minimo) "
                        + "VALUES (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setInt(2, tipo);
            ps.setInt(3, marca);
            ps.setBigDecimal(4, dinero("55000.00"));
            ps.setInt(5, 0);
            return insertarYObtenerId(ps);
        }
    }

    private static int insertarYObtenerId(PreparedStatement ps) throws SQLException {
        if (ps.executeUpdate() != 1) throw new SQLException("No se insertó el fixture");
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) throw new SQLException("MySQL no devolvió el ID de prueba");
            return rs.getInt(1);
        }
    }

    private static int primerId(Connection con, String tabla) throws SQLException {
        // El nombre de la tabla procede exclusivamente de dos constantes internas.
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM " + tabla + " ORDER BY id LIMIT 1")) {
            if (!rs.next()) throw new SQLException("Falta poblar el catálogo: " + tabla);
            return rs.getInt(1);
        }
    }

    private static void limpiarDatos(Datos d) throws SQLException {
        try (Connection con = abrirConexion()) {
            con.setAutoCommit(false);
            try {
                borrarPorId(con, "DELETE FROM proveedores_repuestos WHERE proveedor_id = ?", d.proveedor1);
                borrarPorId(con, "DELETE FROM proveedores_repuestos WHERE proveedor_id = ?", d.proveedor2);
                borrarPorId(con, "DELETE FROM repuestos WHERE id = ?", d.repuesto1);
                borrarPorId(con, "DELETE FROM repuestos WHERE id = ?", d.repuesto2);
                borrarPorId(con, "DELETE FROM proveedores WHERE id = ?", d.proveedor1);
                borrarPorId(con, "DELETE FROM proveedores WHERE id = ?", d.proveedor2);
                con.commit();
            } catch (SQLException | RuntimeException error) {
                try { con.rollback(); } catch (SQLException falloRollback) {
                    error.addSuppressed(falloRollback);
                }
                throw error;
            }
        }
    }

    private static void borrarPorId(Connection con, String sql, int id) throws SQLException {
        if (id <= 0) return;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private static int contarAsociaciones(int proveedorId, int repuestoId) {
        try (Connection con = abrirConexion(); PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM proveedores_repuestos WHERE proveedor_id = ? AND repuesto_id = ?")) {
            ps.setInt(1, proveedorId);
            ps.setInt(2, repuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new AccesoDatosException("Error contando asociaciones", e);
        }
    }

    private static boolean existe(String tabla, int id) {
        if (id <= 0) return false;
        // 'tabla' solo se recibe de constantes internas de esta clase de prueba.
        try (Connection con = abrirConexion(); PreparedStatement ps =
                con.prepareStatement("SELECT 1 FROM " + tabla + " WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new AccesoDatosException("Error consultando existencia", e);
        }
    }

    private static List<String> filas(String sql) {
        List<String> resultado = new ArrayList<>();
        try (Connection con = abrirConexion();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            int columnas = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder fila = new StringBuilder();
                for (int i = 1; i <= columnas; i++) {
                    fila.append(rs.getString(i)).append('\u001F');
                }
                resultado.add(fila.toString());
            }
            return resultado;
        } catch (SQLException e) {
            throw new AccesoDatosException("Error comparando datos históricos", e);
        }
    }

    private static Connection abrirConexion() {
        Connection con = ConexionFactory.getInstancia().crearConexion();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            if (!rs.next() || !"carmotors_test".equals(rs.getString(1))) {
                con.close();
                throw new IllegalStateException("SEGURIDAD: la base activa no es carmotors_test");
            }
            return con;
        } catch (SQLException e) {
            try { con.close(); } catch (SQLException cierre) { e.addSuppressed(cierre); }
            throw new AccesoDatosException("No se pudo verificar la base de pruebas", e);
        }
    }

    private static BigDecimal dinero(String texto) {
        return new BigDecimal(texto);
    }

    private static void comprobar(boolean condicion, String descripcion) {
        if (!condicion) throw new AssertionError("FALLO: " + descripcion);
        System.out.println("[OK] " + (++verificaciones) + " - " + descripcion);
    }

    private static void esperar(Class<? extends Throwable> tipo, Runnable accion,
                                String descripcion) {
        try {
            accion.run();
        } catch (Throwable error) {
            if (tipo.isInstance(error)) {
                comprobar(true, descripcion);
                return;
            }
            throw new AssertionError("Excepción incorrecta en: " + descripcion, error);
        }
        throw new AssertionError("No se produjo la excepción esperada: " + descripcion);
    }

    private static final class Datos {
        int proveedor1;
        int proveedor2;
        int repuesto1;
        int repuesto2;
        int tipo;
        int marca;
        String nombreRepuesto1;
    }
}
