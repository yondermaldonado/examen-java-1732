package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.ProveedorController;
import com.carmotors.dao.factory.ProveedorDAOFactory;
import com.carmotors.excepcion.AccesoDatosException;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Proveedor;
import com.carmotors.servicio.ProveedorService;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Vista de pruebas MANUALES para el issue #40.
 *
 * Ejecutar main() desde IntelliJ. No requiere mvn desde la terminal.
 * Realiza cambios REALES en carmotors_test; no ejecutarla contra produccion.
 */
public class VistaPruebaProveedor {

    private static final String BASE_PERMITIDA = "carmotors_test";

    private final Scanner scanner;
    private final ProveedorController controlador;

    public VistaPruebaProveedor(Scanner scanner, ProveedorController controlador) {
        this.scanner = scanner;
        this.controlador = controlador;
    }

    public static void main(String[] args) {
        // Bloquea el programa si la conexion apunta a otra base de datos.
        if (!verificarBaseDePruebas()) {
            return;
        }

        // Factory Method se utiliza aqui, en el ensamblaje de dependencias
        // del programa de prueba. La vista NO construye DAOs por operacion.
        ProveedorService servicio = new ProveedorService(
                new ProveedorDAOFactory(),
                () -> ConexionFactory.getInstancia().crearConexion()
        );
        ProveedorController controlador = new ProveedorController(servicio);

        try (Scanner scanner = new Scanner(System.in)) {
            new VistaPruebaProveedor(scanner, controlador).ejecutar();
        }
    }

    private static boolean verificarBaseDePruebas() {
        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {
            String base = conexion.getCatalog();
            if (!BASE_PERMITIDA.equalsIgnoreCase(base)) {
                System.err.println("ACCESO BLOQUEADO: la base actual es '"
                        + base + "', no '" + BASE_PERMITIDA + "'.");
                return false;
            }
            System.out.println("Conexion verificada: " + base);
            System.out.println("ATENCION: las operaciones modifican esta base REALMENTE.");
            return true;
        } catch (SQLException | RuntimeException e) {
            System.err.println("No fue posible verificar la base de pruebas: "
                    + e.getMessage());
            return false;
        }
    }

    private void ejecutar() {
        while (true) {
            mostrarMenu();
            String opcion = leerLinea("Selecciona una opcion: ");

            if ("0".equals(opcion)) {
                System.out.println("Saliendo de las pruebas de proveedores.");
                return;
            }

            try {
                switch (opcion) {
                    case "1" -> listar();
                    case "2" -> registrar();
                    case "3" -> buscarPorId();
                    case "4" -> buscarPorNombre();
                    case "5" -> buscarPorEmail();
                    case "6" -> actualizar();
                    case "7" -> eliminar();
                    case "8" -> verificarExistencia();
                    case "9" -> guiaPruebas();
                    default -> System.out.println("Opcion no valida.");
                }
            } catch (IllegalArgumentException | ValidacionException e) {
                System.out.println("DATOS INVALIDOS: " + e.getMessage());
            } catch (AccesoDatosException e) {
                System.out.println("ERROR DE BASE DE DATOS: " + e.getMessage());
                if (e.getCause() instanceof SQLException sql) {
                    System.out.println("Codigo MySQL: " + sql.getErrorCode()
                            + " | SQLState: " + sql.getSQLState());
                }
            } catch (RuntimeException e) {
                System.out.println("ERROR INESPERADO: " + e);
                e.printStackTrace(System.out);
            }

            System.out.println();
            leerLinea("Presiona ENTER para continuar...");
        }
    }

    private void mostrarMenu() {
        System.out.println("\n============================================");
        System.out.println("  CARMOTORS - PRUEBAS MANUALES PROVEEDORES");
        System.out.println("  Base autorizada: " + BASE_PERMITIDA);
        System.out.println("============================================");
        System.out.println(" 1. Listar todos los proveedores");
        System.out.println(" 2. Registrar un proveedor (CREATE)");
        System.out.println(" 3. Buscar proveedor por ID");
        System.out.println(" 4. Buscar proveedores por nombre");
        System.out.println(" 5. Buscar proveedores por correo");
        System.out.println(" 6. Actualizar proveedor (UPDATE)");
        System.out.println(" 7. Eliminar proveedor (DELETE)");
        System.out.println(" 8. Verificar si existe un ID");
        System.out.println(" 9. Guia de pruebas manuales");
        System.out.println(" 0. Salir");
        System.out.println("============================================");
    }

    private void listar() {
        System.out.println("\n--- LISTAR PROVEEDORES ---");
        mostrarLista(controlador.listarProveedores());
    }

    private void registrar() {
        System.out.println("\n--- REGISTRAR PROVEEDOR ---");
        String nombre = leerLinea("Nombre: ");
        String contacto = opcional(leerLinea("Contacto (ENTER si no tiene): "));
        String email = opcional(leerLinea("Correo (ENTER si no tiene): "));

        System.out.println("Se registrara: " + nombre);
        if (!confirmar("CONFIRMAR")) {
            System.out.println("Registro cancelado.");
            return;
        }

        Proveedor creado = controlador.registrarProveedor(nombre, contacto, email);
        System.out.println("PROVEEDOR REGISTRADO. ID generado: " + creado.getId());
        mostrarProveedor(creado);
    }

    private void buscarPorId() {
        System.out.println("\n--- BUSCAR POR ID ---");
        int id = leerId("ID del proveedor: ");
        mostrarResultado(controlador.buscarProveedorPorId(id));
    }

    private void buscarPorNombre() {
        System.out.println("\n--- BUSCAR POR NOMBRE ---");
        String nombre = leerLinea("Nombre o parte del nombre: ");
        mostrarLista(controlador.buscarProveedoresPorNombre(nombre));
    }

    private void buscarPorEmail() {
        System.out.println("\n--- BUSCAR POR CORREO ---");
        String email = leerLinea("Correo exacto: ");
        mostrarLista(controlador.buscarProveedoresPorEmail(email));
    }

    private void actualizar() {
        System.out.println("\n--- ACTUALIZAR PROVEEDOR ---");
        int id = leerId("ID que deseas actualizar: ");
        Optional<Proveedor> encontrado = controlador.buscarProveedorPorId(id);
        if (encontrado.isEmpty()) {
            System.out.println("No existe un proveedor con ese ID.");
            return;
        }

        Proveedor anterior = encontrado.get();
        System.out.println("Datos actuales:");
        mostrarProveedor(anterior);
        System.out.println("ENTER conserva el valor actual.");
        System.out.println("En CONTACTO y CORREO escribe - para borrar el valor.");

        String nombre = conservarSiVacio(
                leerLinea("Nuevo nombre: "), anterior.getNombre());
        String contacto = editarOpcional(
                leerLinea("Nuevo contacto: "), anterior.getContacto());
        String email = editarOpcional(
                leerLinea("Nuevo correo: "), anterior.getEmail());

        // Validamos todos los datos antes de solicitar confirmacion.
        new Proveedor(nombre, contacto, email);

        if (!confirmar("ACTUALIZAR")) {
            System.out.println("Actualizacion cancelada.");
            return;
        }

        boolean actualizado = controlador.actualizarProveedor(
                id, nombre, contacto, email);

        if (actualizado) {
            System.out.println("PROVEEDOR ACTUALIZADO:");
            mostrarResultado(controlador.buscarProveedorPorId(id));
        } else {
            System.out.println("No se actualizo: el proveedor ya no existe.");
        }
    }

    private void eliminar() {
        System.out.println("\n--- ELIMINAR PROVEEDOR ---");
        int id = leerId("ID que deseas eliminar: ");
        Optional<Proveedor> encontrado = controlador.buscarProveedorPorId(id);
        if (encontrado.isEmpty()) {
            System.out.println("El proveedor no existe.");
            return;
        }

        mostrarProveedor(encontrado.get());
        System.out.println("ATENCION: se intentara un DELETE REAL.");
        System.out.println("Si tiene repuestos, lotes o evaluaciones vinculados,");
        System.out.println("la base de datos debe impedir su eliminacion.");

        if (!confirmar("ELIMINAR")) {
            System.out.println("Eliminacion cancelada.");
            return;
        }

        // Si hay clave foranea, el servicio lanza ValidacionException.
        boolean eliminado = controlador.eliminarProveedor(id);
        System.out.println(eliminado
                ? "PROVEEDOR ELIMINADO CORRECTAMENTE."
                : "El proveedor no existia al intentar eliminarlo.");
    }

    private void verificarExistencia() {
        System.out.println("\n--- VERIFICAR EXISTENCIA ---");
        int id = leerId("ID a verificar: ");
        boolean existe = controlador.existeProveedor(id);
        System.out.println(existe
                ? "SI EXISTE un proveedor con ID " + id
                : "NO EXISTE un proveedor con ID " + id);
    }

    private void guiaPruebas() {
        System.out.println("\n--- GUIA DE VERIFICACIONES MANUALES ---");
        System.out.println("A. Opcion 1: observar proveedores existentes de data.sql.");
        System.out.println("B. Opcion 2: crear un proveedor temporal con contacto o correo.");
        System.out.println("C. Opciones 3, 4, 5 y 8: encontrar ese proveedor.");
        System.out.println("D. Opcion 6: actualizarlo y comprobar los nuevos datos.");
        System.out.println("E. Opcion 7: eliminar solo el proveedor temporal creado.");
        System.out.println("F. Opcion 3: comprobar que ya no aparece.");
        System.out.println("G. Para probar integridad referencial, usa un proveedor");
        System.out.println("   que SABES que tiene repuestos asociados. Al intentar");
        System.out.println("   eliminarlo, MySQL debe rechazarlo (error 1451).");
        System.out.println("   NO elijas proveedores reales para comprobar un borrado");
        System.out.println("   exitoso: podrias perder informacion de prueba.");
    }

    private void mostrarResultado(Optional<Proveedor> encontrado) {
        if (encontrado.isPresent()) {
            mostrarProveedor(encontrado.get());
        } else {
            System.out.println("No se encontro ningun proveedor.");
        }
    }

    private void mostrarLista(List<Proveedor> proveedores) {
        if (proveedores.isEmpty()) {
            System.out.println("No se encontraron proveedores.");
            return;
        }
        System.out.println("Se encontraron " + proveedores.size() + " proveedor(es):");
        for (Proveedor proveedor : proveedores) {
            mostrarProveedor(proveedor);
            System.out.println("--------------------------------------------");
        }
    }

    private void mostrarProveedor(Proveedor proveedor) {
        System.out.println("ID       : " + proveedor.getId());
        System.out.println("Nombre   : " + proveedor.getNombre());
        System.out.println("Contacto : " + mostrarOpcional(proveedor.getContacto()));
        System.out.println("Correo   : " + mostrarOpcional(proveedor.getEmail()));
    }

    private String mostrarOpcional(String valor) {
        return valor == null ? "(sin registrar)" : valor;
    }

    private String leerLinea(String mensaje) {
        System.out.print(mensaje);
        return scanner.nextLine().strip();
    }

    private int leerId(String mensaje) {
        String texto = leerLinea(mensaje);
        try {
            int id = Integer.parseInt(texto);
            if (id <= 0) {
                throw new IllegalArgumentException("El ID debe ser mayor que cero.");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "El ID debe ser un numero entero positivo.");
        }
    }

    private String opcional(String texto) {
        return texto.isBlank() ? null : texto;
    }

    private String conservarSiVacio(String texto, String anterior) {
        return texto.isBlank() ? anterior : texto;
    }

    private String editarOpcional(String texto, String anterior) {
        if (texto.isEmpty()) {
            return anterior;
        }
        if ("-".equals(texto)) {
            return null;
        }
        return texto;
    }

    private boolean confirmar(String palabra) {
        String respuesta = leerLinea(
                "Escribe " + palabra + " para confirmar (otro texto cancela): ");
        return palabra.equals(respuesta);
    }
}
