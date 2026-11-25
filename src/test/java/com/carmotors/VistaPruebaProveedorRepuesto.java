package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.ProveedorRepuestoController;
import com.carmotors.dao.factory.ProveedorDAOFactory;
import com.carmotors.dao.factory.ProveedorRepuestoDAOFactory;
import com.carmotors.dao.factory.RepuestoDAOFactory;
import com.carmotors.excepcion.AccesoDatosException;
import com.carmotors.excepcion.ConexionException;
import com.carmotors.excepcion.DaoException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.ProveedorRepuesto;
import com.carmotors.servicio.ProveedorRepuestoService;
import com.carmotors.servicio.ProveedorService;
import com.carmotors.servicio.RepuestoConsulta;
import com.carmotors.servicio.RepuestoService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Supplier;

/**
 * Vista manual para el issue #41.
 * Ejecutar el main desde IntelliJ IDEA con Java 17.
 * Las inserciones y actualizaciones son reales y permanecen en MySQL.
 * Solo permite trabajar sobre carmotors_test.
 */
public final class VistaPruebaProveedorRepuesto {

    private final ProveedorRepuestoController controlador;
    private final Scanner scanner;

    public VistaPruebaProveedorRepuesto(
            ProveedorRepuestoController controlador,
            Scanner scanner) {
        if (controlador == null || scanner == null) {
            throw new IllegalArgumentException(
                    "El controlador y el Scanner son obligatorios");
        }
        this.controlador = controlador;
        this.scanner = scanner;
    }

    public static void main(String[] args) {
        Supplier<Connection> conexiones =
                () -> ConexionFactory.getInstancia().crearConexion();

        // Nunca ejecutar un menú de prueba con escrituras en producción.
        try (Connection conexion = conexiones.get()) {
            String base = conexion.getCatalog();
            if (!"carmotors_test".equalsIgnoreCase(base)) {
                System.err.println("Ejecucion bloqueada: base de datos '"
                        + base + "'. Solo se permite carmotors_test.");
                return;
            }
        } catch (SQLException | RuntimeException e) {
            System.err.println("No se pudo verificar la base de pruebas: "
                    + e.getMessage());
            return;
        }

        ProveedorService proveedores = new ProveedorService(
                new ProveedorDAOFactory(), conexiones);

        RepuestoService servicioRepuestos = new RepuestoService(
                new RepuestoDAOFactory().obtenerDAO(),
                proveedores,
                conexiones);

        // Puerto existente, adaptado sin modificar RepuestoService.
        RepuestoConsulta repuestos = id -> id > 0
                && !servicioRepuestos.buscarPorId(id).isEmpty();

        ProveedorRepuestoService servicioCatalogo =
                new ProveedorRepuestoService(
                        new ProveedorRepuestoDAOFactory(),
                        proveedores,
                        repuestos,
                        conexiones);

        ProveedorRepuestoController controlador =
                new ProveedorRepuestoController(servicioCatalogo);

        try (Scanner scanner = new Scanner(System.in)) {
            new VistaPruebaProveedorRepuesto(controlador, scanner).iniciar();
        }
    }

    private void iniciar() {
        System.out.println("\n=== CARMOTORS | CATALOGO PROVEEDOR-REPUESTO ===");
        System.out.println("Base de datos: carmotors_test");
        System.out.println("ATENCION: registrar y actualizar guardan cambios reales.");
        System.out.println("Salir del programa NO deshace los cambios.");

        boolean salir = false;
        while (!salir) {
            imprimirMenu();
            String opcion = leerTexto("Opcion: ");

            try {
                switch (opcion) {
                    case "1" -> registrar();
                    case "2" -> buscar();
                    case "3" -> listarPorProveedor();
                    case "4" -> listarPorRepuesto();
                    case "5" -> actualizarCosto();
                    case "6" -> mostrarAyuda();
                    case "0" -> {
                        salir = true;
                        System.out.println("Saliendo del catalogo.");
                    }
                    default -> System.out.println("Opcion no valida.");
                }
            } catch (NumberFormatException e) {
                System.out.println("ERROR: el ID debe ser un numero entero.");
            } catch (ValidacionException | IllegalArgumentException e) {
                System.out.println("VALIDACION: " + e.getMessage());
            } catch (AccesoDatosException | DaoException | ConexionException e) {
                System.out.println("ERROR DE DATOS: " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("Causa tecnica: " + e.getCause());
                }
            } catch (RuntimeException e) {
                System.out.println("ERROR NO PREVISTO: " + e);
                e.printStackTrace(System.out);
            }
        }
    }

    private void imprimirMenu() {
        System.out.println("\n---------------------------------------------");
        System.out.println("1. Asociar un proveedor a un repuesto");
        System.out.println("2. Buscar asociacion exacta");
        System.out.println("3. Listar repuestos por proveedor");
        System.out.println("4. Listar proveedores por repuesto");
        System.out.println("5. Actualizar el costo de una asociacion");
        System.out.println("6. Ayuda / escenarios manuales");
        System.out.println("0. Salir");
        System.out.println("---------------------------------------------");
    }

    private void registrar() {
        System.out.println("\n--- REGISTRAR ASOCIACION ---");
        int proveedorId = leerId("ID del proveedor: ");
        int repuestoId = leerId("ID del repuesto: ");
        String costo = leerTexto("Costo (ejemplo 32000.50): ");

        ProveedorRepuesto registrada = controlador.registrarAsociacion(
                proveedorId, repuestoId, costo);

        System.out.println("Asociacion registrada correctamente:");
        mostrarAsociacion(registrada);
    }

    private void buscar() {
        System.out.println("\n--- BUSCAR ASOCIACION ---");
        int proveedorId = leerId("ID del proveedor: ");
        int repuestoId = leerId("ID del repuesto: ");

        Optional<ProveedorRepuesto> resultado =
                controlador.buscarAsociacion(proveedorId, repuestoId);

        if (resultado.isPresent()) {
            mostrarAsociacion(resultado.get());
        } else {
            System.out.println("La asociacion no existe.");
        }
    }

    private void listarPorProveedor() {
        System.out.println("\n--- REPUESTOS DE UN PROVEEDOR ---");
        int proveedorId = leerId("ID del proveedor: ");
        mostrarLista(controlador.listarPorProveedor(proveedorId));
    }

    private void listarPorRepuesto() {
        System.out.println("\n--- PROVEEDORES DE UN REPUESTO ---");
        int repuestoId = leerId("ID del repuesto: ");
        mostrarLista(controlador.listarPorRepuesto(repuestoId));
    }

    private void actualizarCosto() {
        System.out.println("\n--- ACTUALIZAR COSTO ---");
        int proveedorId = leerId("ID del proveedor: ");
        int repuestoId = leerId("ID del repuesto: ");

        Optional<ProveedorRepuesto> actual =
                controlador.buscarAsociacion(proveedorId, repuestoId);

        if (actual.isEmpty()) {
            System.out.println("No existe la asociacion. No se modifico nada.");
            return;
        }

        System.out.println("Costo actual: "
                + actual.get().getCosto().toPlainString());
        String nuevoCosto = leerTexto("Nuevo costo: ");
        String confirmacion = leerTexto("Confirmar cambio (SI/no): ");

        if (!"SI".equalsIgnoreCase(confirmacion)) {
            System.out.println("Actualizacion cancelada.");
            return;
        }

        boolean actualizado = controlador.actualizarCosto(
                proveedorId, repuestoId, nuevoCosto);

        if (actualizado) {
            System.out.println("Costo actualizado correctamente.");
            controlador.buscarAsociacion(proveedorId, repuestoId)
                    .ifPresent(this::mostrarAsociacion);
        } else {
            System.out.println("La asociacion ya no existe. Sin actualizacion.");
        }
    }

    private void mostrarLista(List<ProveedorRepuesto> asociaciones) {
        if (asociaciones.isEmpty()) {
            System.out.println("No se encontraron asociaciones.");
            return;
        }

        System.out.println("Asociaciones encontradas: " + asociaciones.size());
        System.out.println("Proveedor ID | Repuesto ID | Costo");
        System.out.println("---------------------------------");
        for (ProveedorRepuesto asociacion : asociaciones) {
            mostrarAsociacion(asociacion);
        }
    }

    private void mostrarAsociacion(ProveedorRepuesto asociacion) {
        System.out.printf("%-12d | %-10d | %s%n",
                asociacion.getProveedorId(),
                asociacion.getRepuestoId(),
                asociacion.getCosto().toPlainString());
    }

    private void mostrarAyuda() {
        System.out.println("\n--- GUIA DE PRUEBA MANUAL ---");
        System.out.println("1) Consulta las asociaciones de un proveedor conocido.");
        System.out.println("2) Consulta los proveedores del mismo repuesto.");
        System.out.println("3) Registra una combinacion que no exista.");
        System.out.println("4) Intenta registrar esa combinacion otra vez: debe fallar.");
        System.out.println("5) Actualiza su costo y verifica el cambio consultandola.");
        System.out.println("6) Repite el mismo costo: debe funcionar sin duplicar.");
        System.out.println("7) Prueba costo -1, texto no numerico e IDs inexistentes.");
        System.out.println("NOTA: este menu no elimina asociaciones ni restaura costos.");
        System.out.println("Usa una base de pruebas aislada o datos preparados para ello.");
    }

    private int leerId(String mensaje) {
        int id = Integer.parseInt(leerTexto(mensaje));
        if (id <= 0) {
            throw new ValidacionException("El ID debe ser mayor que cero.");
        }
        return id;
    }

    private String leerTexto(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().trim();
    }
}
