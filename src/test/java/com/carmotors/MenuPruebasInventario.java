package com.carmotors;

import com.carmotors.modelo.Lote;
import com.carmotors.modelo.LoteLinea;
import com.carmotors.modelo.StockLote;
import com.carmotors.modelo.StockRepuesto;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * Menu manual para probar inventario (issue #35).
 *
 * Este archivo SOLO recibe opciones y muestra resultados.
 * La logica real ya esta en los Controller y Service del proyecto.
 *
 * Cuando el usuario elige una opcion:
 * 1. El menu lee los datos.
 * 2. Llama al Controller correspondiente (o al Service si es una prueba directa).
 * 3. El Service valida y consulta al DAO.
 * 4. El DAO accede a MySQL.
 * 5. El menu muestra el resultado.
 *
 * Las opciones que escriben en MySQL requieren carmotors_test y confirmacion.
 */
public final class MenuPruebasInventario {

    private final Scanner entrada = new Scanner(System.in);

    // Todos los Controller, Service y DAO se crean en ContextoInventario.
    private final ContextoInventario contexto = new ContextoInventario();

    /** Punto de entrada del programa. */
    public static void main(String[] args) {
        MenuPruebasInventario menu = new MenuPruebasInventario();
        menu.iniciar();
    }

    /** Muestra el menu principal hasta que el usuario elige salir. */
    private void iniciar() {
        System.out.println("\n=== CarMotors | Pruebas de inventario (Issue #35) ===");
        System.out.println("Base detectada: " + contexto.baseActual());
        System.out.println("Las escrituras se permiten SOLO en carmotors_test.");

        boolean continuar = true;
        while (continuar) {
            mostrarMenuPrincipal();
            String opcion = leer("Opcion");

            // Cada case llama a un metodo de ESTE menu.
            // Asi podemos entender una opcion sin mezclarla con las demas.
            try {
                switch (opcion) {
                    case "1": menuRepuestos(); break;
                    case "2": menuLotes(); break;
                    case "3": menuExistencias(); break;
                    case "4": menuAlertas(); break;
                    case "5": menuValidaciones(); break;
                    case "6": verificarConsistencia(); break;
                    case "7": menuTiposRepuesto(); break;
                    case "0":
                        System.out.println("Fin de las pruebas.");
                        continuar = false;
                        break;
                    default:
                        System.out.println("Opcion no valida.");
                        break;
                }
            } catch (RuntimeException error) {
                // Evita que una entrada equivocada cierre todo el menu.
                System.out.println("ERROR: " + error.getClass().getSimpleName()
                        + ": " + error.getMessage());
            }
        }
    }

    private void mostrarMenuPrincipal() {
        System.out.println("\n1. CRUD repuestos");
        System.out.println("2. Ingreso y consulta de lotes");
        System.out.println("3. Existencias y reservas");
        System.out.println("4. Alertas Observer");
        System.out.println("5. Validaciones y rollback");
        System.out.println("6. Consistencia de data.sql");
        System.out.println("7. CRUD tipos de repuesto");
        System.out.println("0. Salir");
    }

    /** Pide al usuario un texto y le quita los espacios de los extremos. */
    private String leer(String mensaje) {
        System.out.print(mensaje + ": ");
        return entrada.nextLine().trim();
    }

    /** Si el usuario deja la entrada vacia, utiliza el valor por defecto. */
    private String leerConDefecto(String mensaje, String valorDefecto) {
        String respuesta = leer(mensaje + " [" + valorDefecto + "]");
        if (respuesta.isBlank()) {
            return valorDefecto;
        }
        return respuesta;
    }

    /**
     * Comprueba la base de datos antes de crear, modificar o eliminar.
     * ContextoInventario.basePruebasValida() consulta SELECT DATABASE().
     */
    private boolean escrituraPermitida() {
        if (!contexto.basePruebasValida()) {
            System.out.println("BLOQUEADO: se requiere conexion a carmotors_test.");
            return false;
        }

        String confirmacion = leer("Modificara datos de PRUEBA. Escribe SI para continuar");
        return "SI".equalsIgnoreCase(confirmacion);
    }

    // ==================== REPUESTOS ====================

    /** Muestra las acciones de repuestos y llama a RepuestoController. */
    private void menuRepuestos() {
        System.out.println("\n--- Repuestos ---");
        System.out.println("1. Listar");
        System.out.println("2. Buscar por ID");
        System.out.println("3. Buscar por nombre");
        System.out.println("4. Crear");
        System.out.println("5. Actualizar");
        System.out.println("6. Eliminar");
        System.out.println("7. Filtrar por tipo");

        String opcion = leer("Accion");
        switch (opcion) {
            case "1": System.out.println(contexto.repuestosCtrl.listar()); break;
            case "2": System.out.println(contexto.repuestosCtrl.buscarPorId(leer("ID"))); break;
            case "3": System.out.println(contexto.repuestosCtrl.buscarPorNombre(leer("Nombre"))); break;
            case "4": crearRepuesto(); break;
            case "5": actualizarRepuesto(); break;
            case "6": eliminarRepuesto(); break;
            case "7": System.out.println(contexto.repuestosCtrl.listarPorTipo(leer("ID tipo"))); break;
            default: System.out.println("Opcion no valida."); break;
        }
    }

    /** Reune los siete campos que recibe RepuestoController. */
    private String[] leerDatosRepuesto() {
        System.out.println("Tipos de repuesto disponibles:");
        System.out.println(contexto.tiposCtrl.listar());

        String nombre = leer("Nombre");
        String tipo = leer("ID tipo");
        String marca = leer("ID marca");
        String proveedor = leer("ID proveedor");
        String compra = leer("Precio de compra");
        String venta = leer("Precio de venta");
        String minimo = leer("Stock minimo");

        return new String[]{nombre, tipo, marca, proveedor, compra, venta, minimo};
    }

    /** Menu -> RepuestoController.crear -> RepuestoService -> RepuestoDAOJdbc. */
    private void crearRepuesto() {
        if (!escrituraPermitida()) {
            return;
        }
        String[] datos = leerDatosRepuesto();
        String respuesta = contexto.repuestosCtrl.crear(
                datos[0], datos[1], datos[2], datos[3], datos[4], datos[5], datos[6]);
        System.out.println(respuesta);
    }

    /** Menu -> RepuestoController.actualizar -> RepuestoService -> DAO. */
    private void actualizarRepuesto() {
        if (!escrituraPermitida()) {
            return;
        }
        String id = leer("ID a modificar");
        String[] datos = leerDatosRepuesto();
        String respuesta = contexto.repuestosCtrl.actualizar(
                id, datos[0], datos[1], datos[2], datos[3], datos[4], datos[5], datos[6]);
        System.out.println(respuesta);
    }

    /** Menu -> RepuestoController.eliminar -> RepuestoService -> DAO. */
    private void eliminarRepuesto() {
        if (!escrituraPermitida()) {
            return;
        }
        String id = leer("ID a eliminar");
        System.out.println(contexto.repuestosCtrl.eliminar(id));
    }

    // ==================== TIPOS DE REPUESTO ====================

    /** CRUD del catalogo de tipos; no es el mismo CRUD de repuestos. */
    private void menuTiposRepuesto() {
        System.out.println("\n--- Tipos de repuesto ---");
        System.out.println("1. Listar");
        System.out.println("2. Buscar por ID");
        System.out.println("3. Buscar por nombre");
        System.out.println("4. Crear");
        System.out.println("5. Actualizar");
        System.out.println("6. Eliminar");
        System.out.println("7. Ver repuestos de un tipo");

        String opcion = leer("Accion");
        switch (opcion) {
            case "1": System.out.println(contexto.tiposCtrl.listar()); break;
            case "2": System.out.println(contexto.tiposCtrl.buscarPorId(leer("ID tipo"))); break;
            case "3": System.out.println(contexto.tiposCtrl.buscarPorNombre(leer("Nombre o parte del nombre"))); break;
            case "4": crearTipo(); break;
            case "5": actualizarTipo(); break;
            case "6": eliminarTipo(); break;
            case "7": System.out.println(contexto.repuestosCtrl.listarPorTipo(leer("ID tipo"))); break;
            default: System.out.println("Opcion no valida."); break;
        }
    }

    /** Menu -> RepuestoTipoController.crear -> RepuestoTipoService -> DAO. */
    private void crearTipo() {
        if (!escrituraPermitida()) {
            return;
        }
        String nombre = leer("Nombre del nuevo tipo");
        System.out.println(contexto.tiposCtrl.crear(nombre));
    }

    /** Menu -> RepuestoTipoController.actualizar -> RepuestoTipoService -> DAO. */
    private void actualizarTipo() {
        if (!escrituraPermitida()) {
            return;
        }
        String id = leer("ID del tipo");
        String nombre = leer("Nombre nuevo");
        System.out.println(contexto.tiposCtrl.actualizar(id, nombre));
    }

    /** No se pueden eliminar tipos que tengan repuestos asociados. */
    private void eliminarTipo() {
        if (!escrituraPermitida()) {
            return;
        }
        System.out.println("AVISO: no es posible eliminar un tipo con repuestos asociados.");
        String id = leer("ID del tipo");
        System.out.println(contexto.tiposCtrl.eliminar(id));
    }

    // ==================== LOTES ====================

    /** Menu de lotes -> LoteController -> LoteService -> LoteDAOJdbc. */
    private void menuLotes() {
        System.out.println("\n--- Lotes ---");
        System.out.println("1. Buscar por codigo");
        System.out.println("2. Lotes de un repuesto");
        System.out.println("3. Stock simple");
        System.out.println("4. Seleccionar lotes con FEFO");
        System.out.println("5. Registrar ingreso");

        String opcion = leer("Accion");
        switch (opcion) {
            case "1": System.out.println(contexto.lotesCtrl.buscarPorCodigo(leer("Codigo"))); break;
            case "2": System.out.println(contexto.lotesCtrl.listarPorRepuesto(leer("ID repuesto"))); break;
            case "3": System.out.println(contexto.lotesCtrl.consultarStock(leer("ID repuesto"))); break;
            case "4": seleccionarFefo(); break;
            case "5": registrarIngresoLote(); break;
            default: System.out.println("Opcion no valida."); break;
        }
    }

    /** FEFO propone lotes, pero no reserva ni retira unidades fisicamente. */
    private void seleccionarFefo() {
        String idRepuesto = leer("ID repuesto");
        String cantidad = leer("Cantidad solicitada");
        System.out.println(contexto.lotesCtrl.seleccionarFefo(idRepuesto, cantidad));
    }

    /** Registra una linea de ingreso; el Service realiza la transaccion SQL. */
    private void registrarIngresoLote() {
        if (!escrituraPermitida()) {
            return;
        }

        // Paso 1: leer datos generales del lote.
        String codigo = leer("Codigo unico");
        String proveedor = leer("ID proveedor");
        String fecha = leerConDefecto("Fecha ingreso AAAA-MM-DD", LocalDate.now().toString());

        // Paso 2: leer el repuesto que entra en ese lote.
        String repuesto = leer("ID repuesto");
        String cantidad = leer("Cantidad");
        String costo = leer("Costo unitario");
        String caducidad = leer("Fecha caducidad AAAA-MM-DD (opcional)");

        // Paso 3: Controller -> Service -> DAO -> MySQL.
        // Si confirma el commit, el publicador avisa a Observer.
        String respuesta = contexto.lotesCtrl.registrarIngreso(
                codigo, proveedor, fecha, repuesto, cantidad, costo, caducidad);
        System.out.println(respuesta);
        System.out.println("Eventos Observer recibidos: " + contexto.eventos);
    }

    // ==================== EXISTENCIAS ====================

    /** Estas opciones solo consultan; no reservan ni consumen stock. */
    private void menuExistencias() {
        int repuestoId = Integer.parseInt(leer("ID repuesto"));
        System.out.println("\n1. Resumen");
        System.out.println("2. Ver lotes");
        System.out.println("3. Buscar lote especifico");

        String opcion = leer("Accion");
        switch (opcion) {
            case "1": mostrarResumenExistencias(repuestoId); break;
            case "2": mostrarLotesDelRepuesto(repuestoId); break;
            case "3": mostrarExistenciaDeUnLote(repuestoId); break;
            default: System.out.println("Opcion no valida."); break;
        }
    }

    /** Menu -> ExistenciasService.resumen -> ExistenciasDAOJdbc -> MySQL. */
    private void mostrarResumenExistencias(int repuestoId) {
        StockRepuesto stock = contexto.existencias.resumen(repuestoId);
        System.out.println("Fisico: " + stock.fisico());
        System.out.println("Reservado: " + stock.reservadoActivo());
        System.out.println("Disponible: " + stock.disponible());
        System.out.println("Utilizable: " + stock.disponibleUtilizable());
    }

    /** Consulta el detalle fisico y reservado de cada lote del repuesto. */
    private void mostrarLotesDelRepuesto(int repuestoId) {
        List<StockLote> lotes = contexto.existencias.porRepuesto(repuestoId);
        if (lotes.isEmpty()) {
            System.out.println("Sin lotes.");
            return;
        }
        for (StockLote lote : lotes) {
            System.out.println(lote);
        }
    }

    /** Busca una combinacion exacta de lote y repuesto. */
    private void mostrarExistenciaDeUnLote(int repuestoId) {
        int loteId = Integer.parseInt(leer("ID lote"));
        Optional<StockLote> resultado = contexto.existencias.porLote(loteId, repuestoId);

        if (resultado.isPresent()) {
            System.out.println(resultado.get());
        } else {
            System.out.println("No existe esa pareja lote/repuesto.");
        }
    }

    // ==================== ALERTAS OBSERVER ====================

    /** Menu -> AlertasController o AlertasService; sin enviar mensajes externos. */
    private void menuAlertas() {
        System.out.println("\n1. Consultar alertas");
        System.out.println("2. Ultimas alertas");
        System.out.println("3. Eventos recibidos");

        String opcion = leer("Accion");
        switch (opcion) {
            case "1": System.out.println(contexto.alertasCtrl.mostrarReporte()); break;
            case "2": mostrarUltimasAlertas(); break;
            case "3": System.out.println("Repuestos notificados en esta ejecucion: " + contexto.eventos); break;
            default: System.out.println("Opcion no valida."); break;
        }
    }

    /** Lee las alertas que el servicio guardo durante esta ejecucion. */
    private void mostrarUltimasAlertas() {
        // Se usa Object solamente para imprimir cada alerta sin modificar su clase.
        for (Object alerta : contexto.alertas.ultimasAlertas()) {
            System.out.println(alerta);
        }
    }

    // ==================== VALIDACIONES ====================

    /** Pruebas manuales de datos invalidos y rollback. */
    private void menuValidaciones() {
        System.out.println("\n1. Fisico negativo");
        System.out.println("2. Reserva superior al fisico");
        System.out.println("3. Ingreso negativo");
        System.out.println("4. Rollback SQL + Observer");

        String opcion = leer("Accion");
        switch (opcion) {
            case "1": probarFisicoNegativo(); break;
            case "2": probarReservaMayorAlFisico(); break;
            case "3": probarIngresoNegativo(); break;
            case "4": probarRollback(); break;
            default: System.out.println("Opcion no valida."); break;
        }
    }

    /** StockLote debe rechazar una cantidad fisica menor que cero. */
    private void probarFisicoNegativo() {
        try {
            new StockLote(1, "INV", 1, null, -1, 0);
            System.out.println("FALLO: se acepto el dato invalido.");
        } catch (RuntimeException error) {
            mostrarRechazoEsperado(error);
        }
    }

    /** StockLote debe rechazar una reserva mayor al fisico. */
    private void probarReservaMayorAlFisico() {
        try {
            new StockLote(1, "INV", 1, null, 2, 3);
            System.out.println("FALLO: se acepto el dato invalido.");
        } catch (RuntimeException error) {
            mostrarRechazoEsperado(error);
        }
    }

    /** LoteService debe rechazar un ingreso con cantidad negativa. */
    private void probarIngresoNegativo() {
        Lote lote = new Lote();
        lote.setCodigo("INVALIDO-NEG");
        lote.setProveedorId(1);
        lote.setFechaIngreso(LocalDate.now());

        LoteLinea linea = new LoteLinea();
        linea.setRepuestoId(1);
        linea.setCantidadIngresada(-5);
        linea.setCostoUnitario(BigDecimal.ONE);
        lote.agregarLinea(linea);

        try {
            contexto.lotes.registrarIngreso(lote);
            System.out.println("FALLO: se acepto el dato invalido.");
        } catch (RuntimeException error) {
            mostrarRechazoEsperado(error);
        }
    }

    private void mostrarRechazoEsperado(RuntimeException error) {
        System.out.println("OK: rechazado con " + error.getClass().getSimpleName()
                + ": " + error.getMessage());
    }

    /**
     * Prueba que, si falla el guardado de un lote, NO quede guardado a medias
     * y Observer NO confirme ningun cambio.
     *
     * Se usan dos lineas: una valida y otra con un ID inexistente.
     * Solo se ejecuta si el usuario confirma la escritura en carmotors_test.
     */
    private void probarRollback() {
        if (!escrituraPermitida()) {
            return;
        }

        // Paso 1: preparar un lote nuevo.
        int repuestoId = Integer.parseInt(leer("ID de repuesto EXISTENTE"));
        String codigo = "ROLLBACK-DEMO-" + System.currentTimeMillis();

        Lote lote = new Lote();
        lote.setCodigo(codigo);
        lote.setProveedorId(1);
        lote.setFechaIngreso(LocalDate.now());

        // Paso 2: preparar una linea correcta.
        LoteLinea lineaCorrecta = new LoteLinea();
        lineaCorrecta.setRepuestoId(repuestoId);
        lineaCorrecta.setCantidadIngresada(2);
        lineaCorrecta.setCostoUnitario(BigDecimal.ONE);

        // Paso 3: preparar una linea con un ID de repuesto inexistente.
        LoteLinea lineaIncorrecta = new LoteLinea();
        lineaIncorrecta.setRepuestoId(Integer.MAX_VALUE);
        lineaIncorrecta.setCantidadIngresada(2);
        lineaIncorrecta.setCostoUnitario(BigDecimal.ONE);

        lote.agregarLinea(lineaCorrecta);
        lote.agregarLinea(lineaIncorrecta);

        // Paso 4: contar eventos antes de intentar el ingreso.
        int eventosAntes = contexto.eventos.size();
        boolean ocurrioError = false;

        // Paso 5: el Service intenta guardar el lote mediante su DAO.
        // Esperamos un error SQL y que el Service haga rollback.
        try {
            contexto.lotes.registrarIngreso(lote);
        } catch (RuntimeException error) {
            ocurrioError = true;
            System.out.println("Error SQL esperado: " + error.getMessage());
        }

        // Paso 6: comprobar que no se guardo un lote parcial.
        boolean loteGuardado = !contexto.lotes.buscarPorCodigo(codigo).isEmpty();

        // Paso 7: comprobar que Observer no recibio un nuevo evento.
        boolean seNotifico = contexto.eventos.size() > eventosAntes;

        // Paso 8: mostrar el resultado de las tres comprobaciones.
        System.out.println("Error ocurrido: " + ocurrioError);
        System.out.println("Lote parcial: " + loteGuardado);
        System.out.println("Observer emitio evento: " + seNotifico);

        if (ocurrioError && !loteGuardado && !seNotifico) {
            System.out.println("OK: rollback consistente.");
        } else {
            System.out.println("REVISAR: no se cumplio el comportamiento esperado.");
        }
    }

    // ==================== CONSISTENCIA DE DATOS ====================

    /**
     * Compara conteos de tablas con data.sql y detecta cantidades invalidas.
     * Este es un diagnostico de prueba, NO modifica datos.
     * Las consultas SQL estan aqui solamente para esta demostracion manual.
     */
    private void verificarConsistencia() {
        System.out.println("Base actual: " + contexto.baseActual());
        if (!contexto.basePruebasValida()) {
            System.out.println("Se requiere carmotors_test para validar los datos de prueba.");
            return;
        }

        // Estos valores corresponden a data.sql recien cargado.
        String[] tablas = {"repuestos", "lotes", "lotes_repuestos", "reservas_repuestos", "proveedores"};
        int[] esperados = {12, 6, 15, 18, 4};

        try (Connection conexion = contexto.conexiones.get();
             Statement sentencia = conexion.createStatement()) {

            // Paso 1: contar los registros de cada tabla.
            for (int i = 0; i < tablas.length; i++) {
                String sql = "SELECT COUNT(*) FROM " + tablas[i];
                try (ResultSet resultado = sentencia.executeQuery(sql)) {
                    resultado.next();
                    int cantidadActual = resultado.getInt(1);
                    String estado = cantidadActual == esperados[i] ? "[OK]" : "[DIFERENTE]";
                    System.out.printf("%-20s actual %d | data.sql %d %s%n",
                            tablas[i], cantidadActual, esperados[i], estado);
                }
            }

            // Paso 2: buscar lotes con cantidades imposibles.
            String sqlCantidades = "SELECT COUNT(*) FROM lotes_repuestos "
                    + "WHERE cantidad_actual > cantidad_ingresada "
                    + "OR cantidad_actual < 0 OR cantidad_ingresada <= 0";
            try (ResultSet resultado = sentencia.executeQuery(sqlCantidades)) {
                resultado.next();
                System.out.println("Lineas inconsistentes: " + resultado.getLong(1));
            }

            // Paso 3: buscar reservas ACTIVAS superiores al fisico del lote.
            String sqlReservas = "SELECT COUNT(*) FROM ("
                    + "SELECT lr.lote_id, lr.repuesto_id "
                    + "FROM lotes_repuestos lr "
                    + "JOIN reservas_repuestos rr ON rr.lote_id = lr.lote_id "
                    + "AND rr.repuesto_id = lr.repuesto_id "
                    + "JOIN reserva_estados re ON re.id = rr.estado_id "
                    + "AND re.nombre = 'ACTIVA' "
                    + "GROUP BY lr.lote_id, lr.repuesto_id, lr.cantidad_actual "
                    + "HAVING SUM(rr.cantidad) > lr.cantidad_actual"
                    + ") t";
            try (ResultSet resultado = sentencia.executeQuery(sqlReservas)) {
                resultado.next();
                System.out.println("Lotes con reservas activas mayores al fisico: " + resultado.getLong(1));
            }

            System.out.println("Nota: los conteos de data.sql pueden cambiar despues de registrar lotes.");

        } catch (SQLException error) {
            System.out.println("Error SQL: " + error.getMessage());
        }
    }
}
