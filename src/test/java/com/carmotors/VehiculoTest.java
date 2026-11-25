
package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.VehiculoController;
import com.carmotors.dao.ClienteDAO;
import com.carmotors.dao.ClienteDAOJdbc;
import com.carmotors.dao.ClienteVehiculosDAOJdbc;
import com.carmotors.dao.VehiculoDAO;
import com.carmotors.dao.VehiculoDAOJdbc;
import com.carmotors.excepcion.AccesoDatosException;
import com.carmotors.excepcion.ClienteNoEncontradoException;
import com.carmotors.excepcion.VehiculoDuplicadoException;
import com.carmotors.excepcion.VehiculoNoEncontradoException;
import com.carmotors.modelo.Cliente;
import com.carmotors.modelo.Vehiculo;
import com.carmotors.servicio.ClienteService;
import com.carmotors.servicio.VehiculoConsulta;
import com.carmotors.servicio.VehiculoConsultaImpl;
import com.carmotors.servicio.VehiculoService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class VehiculoTest {

    private static int verificaciones = 0;

    public static void main(String[] args) throws Exception {
        String sufijo = UUID.randomUUID().toString()
                .replace("-", "").substring(0, 7)
                .toUpperCase(Locale.ROOT);

        String documento1 = "T1" + sufijo;
        String documento2 = "T2" + sufijo;

        VehiculoDAO vehiculoDAO = new VehiculoDAOJdbc();
        ClienteDAO clienteDAO = new ClienteDAOJdbc();

        ClienteService clienteService = new ClienteService(
                clienteDAO, new ClienteVehiculosDAOJdbc());

        VehiculoService vehiculoService =
                new VehiculoService(vehiculoDAO, clienteDAO);

        VehiculoController controlador =
                new VehiculoController(vehiculoService);

        VehiculoConsulta consulta =
                new VehiculoConsultaImpl(vehiculoDAO);

        // PROTECCION: solo utilizar la base de pruebas.
        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            verificar(
                    "carmotors_test".equalsIgnoreCase(
                            conexion.getCatalog()),
                    "Conectado exclusivamente a carmotors_test"
            );
        }

        int modeloId = buscarModeloDisponible();

        verificar(modeloId > 0,
                "Existe un modelo en modelos_vehiculo");

        try {
            probarModelo();

            Cliente cliente1 = clienteService.registrar(
                    new Cliente(
                            "Prueba Vehiculos Uno",
                            documento1,
                            "3001112222",
                            null
                    )
            );

            Cliente cliente2 = clienteService.registrar(
                    new Cliente(
                            "Prueba Vehiculos Dos",
                            documento2,
                            "3001113333",
                            null
                    )
            );

            verificar(
                    cliente1.getId() > 0 && cliente2.getId() > 0,
                    "Clientes temporales registrados"
            );

            probarDAO(
                    vehiculoDAO,
                    cliente1.getId(),
                    cliente2.getId(),
                    modeloId,
                    sufijo
            );

            probarService(
                    vehiculoService,
                    vehiculoDAO,
                    cliente1.getId(),
                    cliente2.getId(),
                    modeloId,
                    sufijo
            );

            probarController(
                    controlador,
                    cliente1.getId(),
                    cliente2.getId(),
                    modeloId,
                    sufijo
            );

            probarPuerto(
                    vehiculoService,
                    consulta,
                    cliente1.getId(),
                    modeloId,
                    sufijo
            );

            System.out.println("\n========================================");
            System.out.println(
                    "TODAS LAS VERIFICACIONES SUPERADAS: "
                            + verificaciones
            );
            System.out.println("========================================");

        } finally {
            limpiarDatosTemporales(documento1, documento2);
        }
    }

    // =========================================
    // 1. MODELO VEHICULO
    // =========================================

    private static void probarModelo() {

        titulo("MODELO VEHICULO");

        Vehiculo v = new Vehiculo(
                "  ab  c 123  ", 4, 7);

        verificar(v.getId() == 0,
                "Vehiculo nuevo comienza sin ID");

        verificar("ABC123".equals(v.getPlaca()),
                "Placa sin espacios y en mayusculas");

        verificar(v.getClienteId() == 4,
                "Conserva ID del propietario");

        verificar(v.getModeloId() == 7,
                "Conserva ID del modelo");

        verificar(
                "A123".equals(
                        new Vehiculo(
                                " a\t123 ", 1, 1
                        ).getPlaca()
                ),
                "Elimina tabulaciones de la placa"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new Vehiculo(null, 1, 1),
                "Rechaza placa null"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new Vehiculo("  ", 1, 1),
                "Rechaza placa vacia"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new Vehiculo("ABCDEFGHIJK", 1, 1),
                "Rechaza placa mayor a 10"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new Vehiculo("ABC123", 0, 1),
                "Rechaza propietario ID 0"
        );

        esperar(
                IllegalArgumentException.class,
                () -> new Vehiculo("ABC123", 1, -1),
                "Rechaza modelo ID negativo"
        );

        esperar(
                IllegalArgumentException.class,
                () -> v.setId(0),
                "Rechaza ID generado invalido"
        );

        v.setId(100);

        verificar(v.getId() == 100,
                "Asigna ID valido");

        esperar(
                IllegalStateException.class,
                () -> v.setId(101),
                "No permite asignar el ID dos veces"
        );
    }

    // =========================================
    // 2. VEHICULO DAO JDBC
    // =========================================

    private static void probarDAO(
            VehiculoDAO dao,
            int cliente1,
            int cliente2,
            int modeloId,
            String sufijo) throws SQLException {

        titulo("VEHICULO DAO JDBC");

        String p1 = "DA" + sufijo;
        String p2 = "DB" + sufijo;
        String p3 = "DC" + sufijo;

        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            conexion.setAutoCommit(false);

            try {
                verificar(
                        dao.existeModelo(conexion, modeloId),
                        "DAO reconoce modelo existente"
                );

                verificar(
                        !dao.existeModelo(
                                conexion, Integer.MAX_VALUE),
                        "DAO rechaza modelo inexistente"
                );

                Vehiculo uno =
                        new Vehiculo(p1, cliente1, modeloId);

                dao.insertar(conexion, uno);

                int id = uno.getId();

                verificar(id > 0,
                        "DAO inserta y recupera ID generado");

                verificar(
                        dao.buscarPorId(conexion, id).isPresent(),
                        "DAO busca por ID"
                );

                verificar(
                        dao.buscarPorPlaca(conexion, p1).isPresent(),
                        "DAO busca por placa"
                );

                verificar(
                        dao.listar(conexion).stream()
                                .anyMatch(v -> v.getId() == id),
                        "DAO incluye vehiculo en listado general"
                );

                Vehiculo dos =
                        new Vehiculo(p2, cliente1, modeloId);

                dao.insertar(conexion, dos);

                verificar(
                        dao.listarPorCliente(
                                conexion, cliente1).size() == 2,
                        "DAO permite dos vehiculos del mismo propietario"
                );

                verificar(
                        dao.listarPorCliente(
                                conexion, cliente2).isEmpty(),
                        "DAO no mezcla vehiculos de otros propietarios"
                );

                AccesoDatosException duplicada = esperar(
                        AccesoDatosException.class,
                        () -> dao.insertar(
                                conexion,
                                new Vehiculo(
                                        p1, cliente2, modeloId)
                        ),
                        "MySQL rechaza placa UNIQUE duplicada"
                );

                verificar(
                        esErrorMysql(duplicada, 1062),
                        "DAO conserva error MySQL 1062"
                );

                AccesoDatosException fk = esperar(
                        AccesoDatosException.class,
                        () -> dao.insertar(
                                conexion,
                                new Vehiculo(
                                        p3,
                                        Integer.MAX_VALUE,
                                        modeloId
                                )
                        ),
                        "MySQL impide propietario inexistente"
                );

                verificar(
                        esErrorMysql(fk, 1452),
                        "DAO conserva error FK 1452"
                );

                Vehiculo cambio =
                        conId(p3, cliente2, modeloId, id);

                verificar(
                        dao.actualizar(conexion, cambio),
                        "DAO actualiza vehiculo"
                );

                Vehiculo guardado = dao.buscarPorId(
                        conexion, id).orElseThrow();

                verificar(
                        p3.equals(guardado.getPlaca())
                                && guardado.getClienteId() == cliente2,
                        "DAO persiste placa y propietario nuevos"
                );

                verificar(
                        dao.actualizar(conexion, cambio),
                        "DAO acepta UPDATE sin cambios"
                );

                verificar(
                        dao.buscarPorPlaca(conexion, p1).isEmpty(),
                        "DAO no encuentra placa anterior"
                );

                verificar(
                        !dao.actualizar(
                                conexion,
                                conId(
                                        "DX" + sufijo,
                                        cliente1,
                                        modeloId,
                                        Integer.MAX_VALUE
                                )
                        ),
                        "DAO informa UPDATE de ID inexistente"
                );

                verificar(
                        dao.buscarPorId(
                                conexion, Integer.MAX_VALUE).isEmpty(),
                        "DAO devuelve Optional vacio para ID inexistente"
                );

                verificar(
                        dao.eliminar(conexion, dos.getId()),
                        "DAO elimina segundo vehiculo"
                );

                verificar(
                        dao.eliminar(conexion, id),
                        "DAO elimina primer vehiculo"
                );

                verificar(
                        !dao.eliminar(conexion, id),
                        "DAO no elimina dos veces"
                );

            } finally {
                conexion.rollback();
                System.out.println(
                        "[INFO] Rollback del apartado DAO ejecutado"
                );
            }
        }

        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            verificar(
                    dao.buscarPorPlaca(
                            conexion, p1).isEmpty()
                            && dao.buscarPorPlaca(
                            conexion, p2).isEmpty(),
                    "Rollback DAO no deja vehiculos persistidos"
            );
        }
    }

    // =========================================
    // 3. VEHICULO SERVICE
    // =========================================

    private static void probarService(
            VehiculoService servicio,
            VehiculoDAO dao,
            int cliente1,
            int cliente2,
            int modeloId,
            String sufijo) throws SQLException {

        titulo("VEHICULO SERVICE");

        String p1 = "SA" + sufijo;
        String p2 = "SB" + sufijo;
        String p3 = "SC" + sufijo;

        Vehiculo uno = servicio.registrar(
                new Vehiculo(p1, cliente1, modeloId)
        );

        int id1 = uno.getId();

        verificar(
                id1 > 0,
                "Service asigna ID despues de registrar"
        );

        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            verificar(
                    dao.buscarPorId(conexion, id1).isPresent(),
                    "Service confirma el INSERT con commit"
            );
        }

        Vehiculo dos = servicio.registrar(
                new Vehiculo(p2, cliente1, modeloId)
        );

        verificar(
                dos.getId() > 0,
                "Service registra segundo vehiculo del cliente"
        );

        verificar(
                servicio.obtenerPorId(id1).getClienteId() == cliente1,
                "Service recupera el propietario correcto"
        );

        verificar(
                servicio.buscarPorPlaca(
                        "  sa " + sufijo + " "
                ).map(v -> v.getId() == id1).orElse(false),
                "Service normaliza placa al buscar"
        );

        verificar(
                servicio.buscarPorPlaca(
                        "SN" + sufijo).isEmpty(),
                "Service devuelve Optional vacio si no existe placa"
        );

        verificar(
                servicio.listar().stream()
                        .anyMatch(v -> v.getId() == id1),
                "Service lista vehiculos"
        );

        verificar(
                servicio.listarPorCliente(cliente1).size() == 2,
                "Service lista dos vehiculos del mismo propietario"
        );

        verificar(
                servicio.listarPorCliente(cliente2).isEmpty(),
                "Service muestra lista vacia para cliente sin vehiculos"
        );

        esperar(
                VehiculoDuplicadoException.class,
                () -> servicio.registrar(
                        new Vehiculo(p1, cliente2, modeloId)),
                "Service impide registrar placa duplicada"
        );

        esperar(
                ClienteNoEncontradoException.class,
                () -> servicio.registrar(
                        new Vehiculo(
                                "SX" + sufijo,
                                Integer.MAX_VALUE,
                                modeloId
                        )
                ),
                "Service rechaza propietario inexistente"
        );

        esperar(
                IllegalArgumentException.class,
                () -> servicio.registrar(
                        new Vehiculo(
                                "SY" + sufijo,
                                cliente1,
                                Integer.MAX_VALUE
                        )
                ),
                "Service rechaza modelo inexistente"
        );

        esperar(
                IllegalArgumentException.class,
                () -> servicio.buscarPorPlaca("  "),
                "Service rechaza placa de busqueda vacia"
        );

        esperar(
                IllegalArgumentException.class,
                () -> servicio.obtenerPorId(0),
                "Service rechaza ID 0"
        );

        esperar(
                VehiculoNoEncontradoException.class,
                () -> servicio.obtenerPorId(Integer.MAX_VALUE),
                "Service informa vehiculo inexistente"
        );

        esperar(
                ClienteNoEncontradoException.class,
                () -> servicio.listarPorCliente(Integer.MAX_VALUE),
                "Service informa propietario inexistente al listar"
        );

        verificar(
                servicio.actualizar(
                        conId(p1, cliente1, modeloId, id1)
                ).getId() == id1,
                "Service permite UPDATE sin modificar datos"
        );

        esperar(
                VehiculoDuplicadoException.class,
                () -> servicio.actualizar(
                        conId(
                                p1,
                                cliente1,
                                modeloId,
                                dos.getId()
                        )
                ),
                "Service impide UPDATE con placa duplicada"
        );

        esperar(
                VehiculoNoEncontradoException.class,
                () -> servicio.actualizar(
                        conId(
                                "SZ" + sufijo,
                                cliente1,
                                modeloId,
                                Integer.MAX_VALUE
                        )
                ),
                "Service impide UPDATE de ID inexistente"
        );

        servicio.actualizar(
                conId(p3, cliente2, modeloId, id1)
        );

        verificar(
                servicio.obtenerPorId(id1).getClienteId() == cliente2,
                "Service permite cambiar propietario"
        );

        verificar(
                servicio.buscarPorPlaca(p1).isEmpty(),
                "Service libera la placa anterior al actualizar"
        );

        verificar(
                servicio.listarPorCliente(cliente1).size() == 1
                        && servicio.listarPorCliente(cliente2).size() == 1,
                "Service lista vehiculos segun propietario actualizado"
        );

        esperar(
                IllegalArgumentException.class,
                () -> servicio.eliminar(0),
                "Service rechaza eliminar ID 0"
        );

        esperar(
                VehiculoNoEncontradoException.class,
                () -> servicio.eliminar(Integer.MAX_VALUE),
                "Service rechaza eliminar inexistente"
        );

        servicio.eliminar(dos.getId());

        verificar(
                servicio.buscarPorPlaca(p2).isEmpty(),
                "Service elimina segundo vehiculo"
        );

        esperar(
                VehiculoNoEncontradoException.class,
                () -> servicio.eliminar(dos.getId()),
                "Service rechaza doble DELETE"
        );

        servicio.eliminar(id1);

        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            verificar(
                    dao.buscarPorId(conexion, id1).isEmpty(),
                    "Service confirma DELETE con commit"
            );
        }
    }

    // =========================================
    // 4. VEHICULO CONTROLLER
    // =========================================

    private static void probarController(
            VehiculoController controlador,
            int cliente1,
            int cliente2,
            int modeloId,
            String sufijo) {

        titulo("VEHICULO CONTROLLER");

        String p1 = "CA" + sufijo;
        String p2 = "CB" + sufijo;
        String p3 = "CC" + sufijo;

        Vehiculo uno = controlador.registrarVehiculo(
                " ca " + sufijo, cliente1, modeloId
        );

        Vehiculo dos = controlador.registrarVehiculo(
                p2, cliente1, modeloId
        );

        verificar(
                uno.getId() > 0 && dos.getId() > 0,
                "Controller registra dos vehiculos"
        );

        verificar(
                p1.equals(uno.getPlaca()),
                "Controller devuelve placa normalizada"
        );

        verificar(
                controlador.obtenerVehiculoPorId(
                        uno.getId()).getId() == uno.getId(),
                "Controller obtiene por ID"
        );

        verificar(
                controlador.buscarVehiculoPorPlaca(
                                "  ca " + sufijo
                        ).map(v -> v.getId() == uno.getId())
                        .orElse(false),
                "Controller busca por placa normalizada"
        );

        verificar(
                controlador.listarVehiculos().stream()
                        .anyMatch(v -> v.getId() == dos.getId()),
                "Controller lista vehiculos"
        );

        verificar(
                controlador.listarVehiculosPorCliente(
                        cliente1).size() == 2,
                "Controller lista vehiculos por cliente"
        );

        esperar(
                IllegalArgumentException.class,
                () -> controlador.registrarVehiculo(
                        " ", cliente1, modeloId),
                "Controller propaga validacion de placa vacia"
        );

        esperar(
                VehiculoDuplicadoException.class,
                () -> controlador.registrarVehiculo(
                        p1, cliente2, modeloId),
                "Controller propaga placa duplicada"
        );

        esperar(
                VehiculoNoEncontradoException.class,
                () -> controlador.obtenerVehiculoPorId(
                        Integer.MAX_VALUE),
                "Controller propaga vehiculo no encontrado"
        );

        Vehiculo cambio = controlador.actualizarVehiculo(
                dos.getId(), p3, cliente2, modeloId
        );

        verificar(
                cambio.getId() == dos.getId(),
                "Controller actualiza por ID"
        );

        verificar(
                controlador.buscarVehiculoPorPlaca(
                        p3).isPresent(),
                "Controller confirma actualizacion persistida"
        );

        esperar(
                VehiculoDuplicadoException.class,
                () -> controlador.actualizarVehiculo(
                        dos.getId(), p1, cliente2, modeloId),
                "Controller rechaza duplicado en UPDATE"
        );

        controlador.eliminarVehiculo(dos.getId());

        verificar(
                controlador.buscarVehiculoPorPlaca(
                        p3).isEmpty(),
                "Controller elimina vehiculo actualizado"
        );

        controlador.eliminarVehiculo(uno.getId());

        esperar(
                VehiculoNoEncontradoException.class,
                () -> controlador.eliminarVehiculo(
                        uno.getId()),
                "Controller propaga eliminacion repetida"
        );
    }

    // =========================================
    // 5. PUERTO DE CONSULTA PARA ORDENES
    // =========================================

    private static void probarPuerto(
            VehiculoService servicio,
            VehiculoConsulta consulta,
            int cliente1,
            int modeloId,
            String sufijo) {

        titulo("PUERTO DE CONSULTA PARA ORDENES");

        String placa = "PA" + sufijo;

        Vehiculo original = servicio.registrar(
                new Vehiculo(
                        placa, cliente1, modeloId
                )
        );

        verificar(
                consulta.buscarPorId(
                                original.getId()
                        ).map(v -> v.getClienteId() == cliente1)
                        .orElse(false),
                "Puerto devuelve vehiculo y propietario por ID"
        );

        verificar(
                consulta.buscarPorPlaca(
                                " pa " + sufijo
                        ).map(v -> v.getId() == original.getId())
                        .orElse(false),
                "Puerto busca placa normalizada"
        );

        verificar(
                consulta.buscarPorId(
                        Integer.MAX_VALUE).isEmpty(),
                "Puerto devuelve Optional vacio si ID no existe"
        );

        verificar(
                consulta.buscarPorPlaca(
                        "PX" + sufijo).isEmpty(),
                "Puerto devuelve Optional vacio si placa no existe"
        );

        esperar(
                IllegalArgumentException.class,
                () -> consulta.buscarPorId(0),
                "Puerto rechaza ID invalido"
        );

        esperar(
                IllegalArgumentException.class,
                () -> consulta.buscarPorPlaca(" "),
                "Puerto rechaza placa vacia"
        );

        servicio.eliminar(original.getId());

        verificar(
                consulta.buscarPorId(
                        original.getId()).isEmpty(),
                "Puerto refleja eliminacion del vehiculo"
        );
    }

    // =========================================
    // METODOS AUXILIARES
    // =========================================

    private static int buscarModeloDisponible()
            throws SQLException {

        String sql = """
                SELECT id
                FROM modelos_vehiculo
                ORDER BY id
                LIMIT 1
                """;

        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion();
             PreparedStatement ps =
                     conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                throw new IllegalStateException(
                        "No hay modelos en modelos_vehiculo. "
                                + "Carga los datos de prueba antes."
                );
            }

            return rs.getInt(1);
        }
    }

    private static Vehiculo conId(
            String placa,
            int clienteId,
            int modeloId,
            int id) {

        Vehiculo vehiculo = new Vehiculo(
                placa, clienteId, modeloId
        );

        vehiculo.setId(id);

        return vehiculo;
    }

    private static void limpiarDatosTemporales(
            String documento1,
            String documento2) throws SQLException {

        String borrarVehiculos = """
                DELETE FROM vehiculos
                WHERE cliente_id IN (
                    SELECT id
                    FROM clientes
                    WHERE documento_identidad IN (?, ?)
                )
                """;

        String borrarClientes = """
                DELETE FROM clientes
                WHERE documento_identidad IN (?, ?)
                """;

        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            conexion.setAutoCommit(false);

            try (PreparedStatement psV =
                         conexion.prepareStatement(borrarVehiculos);
                 PreparedStatement psC =
                         conexion.prepareStatement(borrarClientes)) {

                psV.setString(1, documento1);
                psV.setString(2, documento2);

                int vehiculosBorrados = psV.executeUpdate();

                psC.setString(1, documento1);
                psC.setString(2, documento2);

                int clientesBorrados = psC.executeUpdate();

                conexion.commit();

                System.out.println(
                        "[INFO] Limpieza: " + vehiculosBorrados
                                + " vehiculo(s) y "
                                + clientesBorrados
                                + " cliente(s) temporal(es) eliminados"
                );

            } catch (SQLException e) {
                conexion.rollback();
                throw e;
            }
        }
    }

    private static boolean esErrorMysql(
            AccesoDatosException e,
            int codigo) {

        return e.getCause() instanceof SQLException sql
                && sql.getErrorCode() == codigo;
    }

    private static void titulo(String texto) {
        System.out.println(
                "\n========== " + texto + " =========="
        );
    }

    private static void verificar(
            boolean condicion,
            String descripcion) {

        if (!condicion) {
            throw new IllegalStateException(
                    "[FALLO] " + descripcion
            );
        }

        verificaciones++;

        System.out.println("[OK] " + descripcion);
    }

    private static <T extends RuntimeException> T esperar(
            Class<T> tipo,
            Runnable accion,
            String descripcion) {

        try {
            accion.run();

        } catch (RuntimeException e) {

            if (!tipo.isInstance(e)) {
                throw new IllegalStateException(
                        "[FALLO] Se esperaba "
                                + tipo.getSimpleName()
                                + " pero se recibio "
                                + e.getClass().getSimpleName()
                                + ": " + descripcion,
                        e
                );
            }

            verificaciones++;

            System.out.println("[OK] " + descripcion);

            return tipo.cast(e);
        }

        throw new IllegalStateException(
                "[FALLO] No se lanzo "
                        + tipo.getSimpleName()
                        + ": " + descripcion
        );
    }
}
