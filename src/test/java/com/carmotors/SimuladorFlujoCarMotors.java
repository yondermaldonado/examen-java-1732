package com.carmotors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CARMOTORS - PROTOTIPO DE EXPERIENCIA DE USUARIO (ISSUE #43)
 *
 * - Java 17. Un solo archivo. Ejecutar main() desde IntelliJ.
 * - Datos ficticios MODIFICABLES solo durante esta ejecucion.
 * - SIN JDBC, SIN ConexionFactory, SIN SQL, SIN archivos, SIN llamadas a Controller.
 * - Esta NO es la arquitectura final MVC: unicamente es una maqueta de navegacion.
 * - Credenciales fijas de demostracion. NO representa autenticacion segura.
 * - Permisos de ejemplo; la aplicacion final debe validarlos tambien en servicios.
 * - Credenciales: admin / demo123 | tecnico / demo123 (o empleado / demo123).
 * - Para reiniciar los datos de prueba, termina la ejecucion y vuelve a abrir main().
 */
public final class SimuladorFlujoCarMotors {
    private final Scanner teclado = new Scanner(System.in);
    private final Map<Integer, Cliente> clientes = new LinkedHashMap<>();
    private final Map<Integer, Vehiculo> vehiculos = new LinkedHashMap<>();
    private final Map<Integer, Tecnico> tecnicos = new LinkedHashMap<>();
    private final Map<Integer, Proveedor> proveedores = new LinkedHashMap<>();
    private final Map<Integer, Repuesto> repuestos = new LinkedHashMap<>();
    private final Map<Integer, Lote> lotes = new LinkedHashMap<>();
    private final Map<Integer, Orden> ordenes = new LinkedHashMap<>();
    private final Map<Integer, Factura> facturas = new LinkedHashMap<>();
    private final Map<Integer, Evaluacion> evaluaciones = new LinkedHashMap<>();
    private final Map<Integer, Solicitud> solicitudes = new LinkedHashMap<>();
    private int siguienteCliente = 4, siguienteVehiculo = 4, siguienteTecnico = 6;
    private int siguienteProveedor = 4, siguienteRepuesto = 5, siguienteLote = 4;
    private int siguienteOrden = 107, siguienteFactura = 3, siguienteEvaluacion = 3, siguienteSolicitud = 1;
    private Rol rol;
    private Integer tecnicoActivo;

    private enum Rol { ADMINISTRADOR, TECNICO }
    private enum Estado {
        PENDIENTE("Pendiente"), EN_PROCESO("En proceso"), COMPLETADO("Completado"),
        FACTURADO("Facturado"), ENTREGADO("Entregado");
        final String etiqueta;
        Estado(String etiqueta) { this.etiqueta = etiqueta; }
        boolean esActiva() { return this == PENDIENTE || this == EN_PROCESO; }
    }
    private enum EstadoSolicitud { PENDIENTE, ATENDIDA, RECHAZADA }
    private static final class IrInicio extends RuntimeException { }
    private static final class CerrarSesion extends RuntimeException { }
    private static final class SalirPrograma extends RuntimeException { }

    private static final class Cliente {
        final int id;
        String nombre, documento, telefono;
        Cliente(int id, String nombre, String documento, String telefono) {
            this.id = id; this.nombre = nombre; this.documento = documento; this.telefono = telefono;
        }
    }
    private static final class Vehiculo {
        final int id, clienteId;
        String placa, descripcion;
        Vehiculo(int id, int clienteId, String placa, String descripcion) {
            this.id = id; this.clienteId = clienteId; this.placa = placa; this.descripcion = descripcion;
        }
    }
    private static final class Tecnico {
        final int id;
        String nombre, especialidad;
        boolean activo = true;
        Tecnico(int id, String nombre, String especialidad) {
            this.id = id; this.nombre = nombre; this.especialidad = especialidad;
        }
    }
    private static final class Proveedor {
        final int id;
        String nombre, contacto;
        boolean activo = true;
        final Map<Integer, BigDecimal> costoOfertado = new LinkedHashMap<>();
        Proveedor(int id, String nombre, String contacto) {
            this.id = id; this.nombre = nombre; this.contacto = contacto;
        }
    }
    private static final class Repuesto {
        final int id;
        String nombre;
        BigDecimal precioVenta;
        int minimo;
        Repuesto(int id, String nombre, BigDecimal precioVenta, int minimo) {
            this.id = id; this.nombre = nombre; this.precioVenta = precioVenta; this.minimo = minimo;
        }
    }
    private static final class Lote {
        final int id, proveedorId, repuestoId, cantidadOriginal;
        final String codigo;
        final LocalDate ingreso;
        final BigDecimal costoAdquisicion;
        int cantidadActual;
        Lote(int id, int proveedorId, int repuestoId, int cantidad, BigDecimal costo, String codigo) {
            this.id = id; this.proveedorId = proveedorId; this.repuestoId = repuestoId;
            this.cantidadOriginal = cantidad; this.cantidadActual = cantidad;
            this.costoAdquisicion = costo; this.codigo = codigo; this.ingreso = LocalDate.now();
        }
    }
    private static final class Orden {
        final int id, vehiculoId;
        final String tipo, problema;
        final LocalDate fechaCreacion = LocalDate.now();
        Estado estado = Estado.PENDIENTE;
        final Set<Integer> tecnicos = new LinkedHashSet<>();
        final Map<Integer, BigDecimal> horas = new LinkedHashMap<>();
        final Map<Integer, Integer> reservas = new LinkedHashMap<>();
        final Map<Integer, BigDecimal> preciosHistoricos = new LinkedHashMap<>();
        final Map<Integer, Integer> consumos = new LinkedHashMap<>();
        Integer facturaId;
        Orden(int id, int vehiculoId, String tipo, String problema) {
            this.id = id; this.vehiculoId = vehiculoId; this.tipo = tipo; this.problema = problema;
        }
    }
    private static final class Factura {
        final int id, ordenId;
        final BigDecimal repuestos, manoObra, impuestos, total;
        final LocalDate fecha = LocalDate.now();
        Factura(int id, int ordenId, BigDecimal repuestos, BigDecimal manoObra, BigDecimal impuestos) {
            this.id = id; this.ordenId = ordenId; this.repuestos = repuestos; this.manoObra = manoObra;
            this.impuestos = impuestos; this.total = repuestos.add(manoObra).add(impuestos);
        }
    }
    private static final class Evaluacion {
        final int id, proveedorId, loteId, calidad, puntualidad;
        final String observaciones;
        Evaluacion(int id, int proveedorId, int loteId, int calidad, int puntualidad, String observaciones) {
            this.id = id; this.proveedorId = proveedorId; this.loteId = loteId;
            this.calidad = calidad; this.puntualidad = puntualidad; this.observaciones = observaciones;
        }
    }
    private static final class Solicitud {
        final int id, ordenId, tecnicoId, repuestoId, cantidad;
        EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;
        Solicitud(int id, int ordenId, int tecnicoId, int repuestoId, int cantidad) {
            this.id = id; this.ordenId = ordenId; this.tecnicoId = tecnicoId;
            this.repuestoId = repuestoId; this.cantidad = cantidad;
        }
    }

    public static void main(String[] args) {
        SimuladorFlujoCarMotors demo = new SimuladorFlujoCarMotors();
        demo.sembrarDatos();
        demo.ejecutar();
    }

    private static BigDecimal dinero(String valor) { return new BigDecimal(valor).setScale(2); }
    private String pesos(BigDecimal valor) { return "$" + valor.setScale(2, RoundingMode.HALF_UP).toPlainString() + " COP"; }

    private void sembrarDatos() {
        clientes.put(1, new Cliente(1, "Lucía Torres", "1001001", "310000001"));
        clientes.put(2, new Cliente(2, "Manuel Contreras", "1001002", "310000002"));
        clientes.put(3, new Cliente(3, "Sara Castro", "1001003", "310000003"));
        vehiculos.put(1, new Vehiculo(1, 1, "ABC123", "Chevrolet Spark 2018"));
        vehiculos.put(2, new Vehiculo(2, 2, "XYZ85F", "Suzuki GN125 2021"));
        vehiculos.put(3, new Vehiculo(3, 3, "DEF456", "Renault Duster 2020"));
        tecnicos.put(1, new Tecnico(1, "Andrés Rueda", "Mecánica general"));
        tecnicos.put(2, new Tecnico(2, "Carlos Pérez", "Frenos y suspensión"));
        tecnicos.put(3, new Tecnico(3, "María Torres", "Electricidad"));
        tecnicos.put(4, new Tecnico(4, "Luis Gómez", "Motor"));
        Tecnico antiguo = new Tecnico(5, "Jorge Martínez", "Diagnóstico");
        antiguo.activo = false;
        tecnicos.put(5, antiguo);
        proveedores.put(1, new Proveedor(1, "Autopartes Oriente", "ventas@oriente.demo"));
        proveedores.put(2, new Proveedor(2, "Repuestos Norte", "contacto@norte.demo"));
        proveedores.put(3, new Proveedor(3, "Distribuidora Motor", "servicio@motor.demo"));
        repuestos.put(1, new Repuesto(1, "Filtro de aceite", dinero("35000"), 5));
        repuestos.put(2, new Repuesto(2, "Pastillas de freno", dinero("125000"), 3));
        repuestos.put(3, new Repuesto(3, "Aceite de motor (1L)", dinero("50000"), 5));
        repuestos.put(4, new Repuesto(4, "Correa de distribución", dinero("240000"), 2));
        proveedores.get(1).costoOfertado.put(1, dinero("21000"));
        proveedores.get(1).costoOfertado.put(3, dinero("31000"));
        proveedores.get(2).costoOfertado.put(2, dinero("82000"));
        proveedores.get(3).costoOfertado.put(4, dinero("165000"));
        lotes.put(1, new Lote(1, 1, 1, 12, dinero("20000"), "LOT-001"));
        lotes.put(2, new Lote(2, 2, 2, 8, dinero("80000"), "LOT-002"));
        lotes.put(3, new Lote(3, 1, 3, 20, dinero("30000"), "LOT-003"));
        // Orden 101 sin asignación; 102 y 103 activas; 104 completada, 105 facturada, 106 entregada.
        Orden p = new Orden(101, 1, "Preventivo", "Revisión general");
        ordenes.put(p.id, p);
        Orden a = new Orden(102, 2, "Correctivo", "Ruido en frenos");
        a.estado = Estado.EN_PROCESO; asignarDemo(a, 1, dinero("3.00"));
        ordenes.put(a.id, a);
        Orden b = new Orden(103, 3, "Correctivo", "Vibración al acelerar");
        b.estado = Estado.EN_PROCESO; asignarDemo(b, 2, dinero("1.50"));
        ordenes.put(b.id, b);
        Orden c = new Orden(104, 1, "Preventivo", "Cambio de filtro");
        c.estado = Estado.COMPLETADO; asignarDemo(c, 1, dinero("2.00"));
        c.consumos.put(1, 1); c.preciosHistoricos.put(1, dinero("35000"));
        lotes.get(1).cantidadActual--; ordenes.put(c.id, c);
        Orden d = new Orden(105, 2, "Preventivo", "Cambio de aceite");
        d.estado = Estado.FACTURADO; asignarDemo(d, 2, dinero("2.50"));
        d.consumos.put(3, 1); d.preciosHistoricos.put(3, dinero("50000"));
        lotes.get(3).cantidadActual--;
        Factura fd = new Factura(1, d.id, dinero("50000"), dinero("60000"), dinero("0"));
        d.facturaId = fd.id; facturas.put(fd.id, fd); ordenes.put(d.id, d);
        Orden e = new Orden(106, 3, "Correctivo", "Revisión de frenos");
        e.estado = Estado.ENTREGADO; asignarDemo(e, 2, dinero("1.00"));
        Factura fe = new Factura(2, e.id, dinero("0"), dinero("45000"), dinero("0"));
        e.facturaId = fe.id; facturas.put(fe.id, fe); ordenes.put(e.id, e);
        evaluaciones.put(1, new Evaluacion(1, 1, 1, 5, 4, "Llegó correctamente"));
        evaluaciones.put(2, new Evaluacion(2, 2, 2, 4, 3, "Buen producto, demora leve"));
    }

    private void asignarDemo(Orden orden, int tecnicoId, BigDecimal horas) {
        orden.tecnicos.add(tecnicoId);
        orden.horas.put(tecnicoId, horas);
    }

    private void ejecutar() {
        cabecera("BIENVENIDO A CARMOTORS", "Prototipo de consola - datos ficticios en memoria");
        System.out.println("  Este programa NO se conecta a MySQL ni modifica tu proyecto.");
        System.out.println("  Los cambios se pierden al cerrar el programa.");
        while (true) {
            try {
                if (rol == null) login();
                if (rol == Rol.ADMINISTRADOR) menuAdministrador();
                else menuTecnico();
            } catch (IrInicio ignored) {
                // M desde cualquier submenú: el próximo ciclo abre el panel del rol.
            } catch (CerrarSesion ignored) {
                cerrarSesionActual();
            } catch (SalirPrograma fin) {
                despedida();
                return;
            }
        }
    }

    private void cerrarSesionActual() {
        rol = null; tecnicoActivo = null;
        cabecera("SESIÓN CERRADA", "Los datos ficticios aún permanecen en memoria");
        System.out.println("  Para reiniciar los datos, cierra y vuelve a ejecutar el programa.");
    }

    private void despedida() {
        System.out.println("\nGracias por probar CarMotors. No se modificó ningún dato real.");
    }

    private void login() {
        while (true) {
            cabecera("ACCESO DEMOSTRATIVO", "1. Administrador  |  2. Técnico  |  0. Salir");
            String op = entrada("Elige perfil");
            if (op.equals("0")) throw new SalirPrograma();
            if (!op.equals("1") && !op.equals("2")) { aviso("Elige 1, 2 o 0."); continue; }
            String usuario = entrada("Usuario");
            String contrasena = entrada("Contraseña");
            boolean admin = op.equals("1") && usuario.equalsIgnoreCase("admin") && contrasena.equals("demo123");
            boolean tecnico = op.equals("2") && (usuario.equalsIgnoreCase("tecnico") || usuario.equalsIgnoreCase("empleado")) && contrasena.equals("demo123");
            if (!admin && !tecnico) { aviso("Credenciales demo incorrectas. admin / demo123 o tecnico / demo123."); continue; }
            tecnicoActivo = null;
            if (tecnico) {
                aviso("Acceso compartido DEMO: seleccionar un nombre no verifica tu identidad real.");
                Tecnico t = elegirTecnico(true);
                if (t == null) { rol = null; continue; }
                tecnicoActivo = t.id;
            }
            rol = admin ? Rol.ADMINISTRADOR : Rol.TECNICO;
            return;
        }
    }

    private void menuAdministrador() {
        while (true) {
            cabecera("PANEL DEL ADMINISTRADOR", "Jefe del taller | Sin dashboard: menú de navegación");
            System.out.println("  1. Supervisión del taller");
            System.out.println("  2. Atención y órdenes de servicio");
            System.out.println("  3. Gestión administrativa (CRUD)");
            System.out.println("  4. Inventario y abastecimiento");
            System.out.println("  5. Facturación y entregas");
            System.out.println("  6. Reportes e indicadores");
            System.out.println("  0. Cerrar sesión          S. Salir del programa");
            switch (entrada("Opción").toUpperCase(Locale.ROOT)) {
                case "1" -> menuSupervision();
                case "2" -> menuAtencion();
                case "3" -> menuGestion();
                case "4" -> menuInventario(true);
                case "5" -> menuFacturacion();
                case "6" -> menuReportes();
                case "0" -> throw new CerrarSesion();
                case "S" -> throw new SalirPrograma();
                default -> aviso("Opción inexistente. Prueba de nuevo.");
            }
        }
    }

    private void menuTecnico() {
        while (true) {
            Tecnico actual = tecnicos.get(tecnicoActivo);
            cabecera("PANEL TÉCNICO", "Perfil compartido | Seleccionado: " + (actual == null ? "Sin técnico" : actual.nombre));
            System.out.println("  1. Trabajos del taller");
            System.out.println("  2. Crear nueva orden de servicio");
            System.out.println("  3. Consultar inventario y solicitar repuestos [SOLICITUD SIMULADA]");
            System.out.println("  4. Registrar recepción de lote");
            System.out.println("  5. Consultar clientes y vehículos");
            System.out.println("  6. Consultar historial de mantenimiento");
            System.out.println("  7. Cambiar técnico seleccionado");
            System.out.println("  0. Cerrar sesión          S. Salir del programa");
            switch (entrada("Opción").toUpperCase(Locale.ROOT)) {
                case "1" -> menuTrabajos(false);
                case "2" -> crearOrden();
                case "3" -> menuConsultaInventarioTecnico();
                case "4" -> recibirLote();
                case "5" -> consultarClientesVehiculos();
                case "6" -> consultaHistorialVehiculos();
                case "7" -> { Tecnico nuevo = elegirTecnico(true); if (nuevo != null) tecnicoActivo = nuevo.id; }
                case "0" -> throw new CerrarSesion();
                case "S" -> throw new SalirPrograma();
                default -> aviso("Opción inexistente. Prueba de nuevo.");
            }
        }
    }

    private void menuSupervision() {
        while (true) {
            cabecera("SUPERVISIÓN DEL TALLER", "ADMIN | asignaciones, horas y control de estados");
            System.out.println("  1. Técnicos con órdenes activas");
            System.out.println("  2. Técnicos sin órdenes activas");
            System.out.println("  3. Consultar trabajos por técnico");
            System.out.println("  4. Asignar técnico a orden");
            System.out.println("  5. Retirar asignación");
            System.out.println("  6. Registrar horas ADICIONALES [simulación acumulativa]");
            System.out.println("  7. Consultar órdenes por estado");
            System.out.println("  0. Volver             M. Menú principal");
            String op = entrada("Opción");
            switch (op) {
                case "1" -> pantallaTecnicosPorCarga(true, true);
                case "2" -> pantallaTecnicosPorCarga(false, true);
                case "3" -> menuTrabajos(true);
                case "4" -> asignarTecnico();
                case "5" -> retirarTecnico();
                case "6" -> acumularHoras();
                case "7" -> consultaOrdenesPorEstado();
                case "0" -> { return; }
                default -> aviso("Selecciona una opción válida.");
            }
        }
    }

    private void menuAtencion() {
        while (true) {
            cabecera("ATENCIÓN Y ÓRDENES", "ADMIN | recepción, creación e inicio de trabajos");
            System.out.println("  1. Registrar cliente");
            System.out.println("  2. Registrar vehículo");
            System.out.println("  3. Crear orden de servicio");
            System.out.println("  4. Consultar órdenes");
            System.out.println("  5. Iniciar orden: Pendiente -> En proceso");
            System.out.println("  6. Consultar historial de vehículo");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> crearCliente();
                case "2" -> crearVehiculo();
                case "3" -> crearOrden();
                case "4" -> consultaOrdenesPorEstado();
                case "5" -> iniciarOrden();
                case "6" -> consultaHistorialVehiculos();
                case "0" -> { return; }
                default -> aviso("Selecciona una opción válida.");
            }
        }
    }

    private void menuGestion() {
        while (true) {
            cabecera("GESTIÓN ADMINISTRATIVA", "ADMIN | centraliza los CRUD; otros menús NO duplican su lógica");
            System.out.println("  1. Clientes: registrar, consultar, editar, eliminar");
            System.out.println("  2. Vehículos: registrar, consultar, editar, eliminar");
            System.out.println("  3. Técnicos: registrar, consultar, editar, desactivar");
            System.out.println("  4. Proveedores: CRUD, ofertas y evaluaciones");
            System.out.println("  5. Catálogo de repuestos: registrar, consultar, editar, eliminar");
            System.out.println("  6. Ir a atención y órdenes");
            System.out.println("  7. Ir a inventario y abastecimiento");
            System.out.println("  8. Ir a facturación");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> gestionClientes();
                case "2" -> gestionVehiculos();
                case "3" -> gestionTecnicos();
                case "4" -> gestionProveedores();
                case "5" -> gestionRepuestos();
                case "6" -> menuAtencion();
                case "7" -> menuInventario(true);
                case "8" -> menuFacturacion();
                case "0" -> { return; }
                default -> aviso("Selecciona una opción válida.");
            }
        }
    }

    private void menuInventario(boolean administrador) {
        while (true) {
            cabecera("INVENTARIO Y ABASTECIMIENTO", administrador ? "ADMIN | gestión del almacén" : "TÉCNICO | consultas");
            System.out.println("  1. Consultar repuestos y existencias");
            System.out.println("  2. Consultar lotes y costos históricos de adquisición");
            System.out.println("  3. Consultar alertas de stock mínimo");
            if (administrador) {
                System.out.println("  4. Registrar ingreso de lote");
                System.out.println("  5. Reservar / ajustar repuestos de una orden");
                System.out.println("  6. Solicitudes pendientes de repuestos [SOLO DEMO]");
            }
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> consultaRepuestos();
                case "2" -> consultaLotes();
                case "3" -> consultaAlertas();
                case "4" -> { if (administrador) recibirLote(); else aviso("Opción no permitida."); }
                case "5" -> { if (administrador) gestionarReservas(); else aviso("Opción no permitida."); }
                case "6" -> { if (administrador) gestionarSolicitudes(); else aviso("Opción no permitida."); }
                case "0" -> { return; }
                default -> aviso("Selecciona una opción válida.");
            }
        }
    }

    private void menuConsultaInventarioTecnico() {
        while (true) {
            cabecera("REPUESTOS PARA TÉCNICOS", "Consulta de stock + solicitud ficticia al administrador");
            System.out.println("  1. Consultar existencias");
            System.out.println("  2. Consultar alertas");
            System.out.println("  3. Solicitar repuesto para una orden [MEJORA FUTURA, SOLO DEMO]");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> consultaRepuestos();
                case "2" -> consultaAlertas();
                case "3" -> solicitarRepuesto();
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void menuFacturacion() {
        while (true) {
            cabecera("FACTURACIÓN Y ENTREGAS", "ADMIN | una factura por orden completada");
            System.out.println("  1. Ver órdenes completadas pendientes de factura");
            System.out.println("  2. Facturar orden completada");
            System.out.println("  3. Consultar facturas emitidas");
            System.out.println("  4. Registrar entrega de vehículo");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> consultaOrdenes(Estado.COMPLETADO);
                case "2" -> facturar();
                case "3" -> consultaFacturas();
                case "4" -> entregar();
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void menuReportes() {
        while (true) {
            cabecera("REPORTES E INDICADORES", "ADMIN | cifras derivadas de los datos ficticios actuales");
            System.out.println("  1. Órdenes por estado y tipo de mantenimiento");
            System.out.println("  2. Productividad por técnico (participación en trabajos)");
            System.out.println("  3. Consumo de repuestos");
            System.out.println("  4. Calidad y puntualidad de proveedores");
            System.out.println("  5. Alertas de inventario");
            System.out.println("  6. Facturación simulada por período [FUTURO EN PROYECTO REAL]");
            System.out.println("  7. Estadísticas de compras [FUTURO EN PROYECTO REAL]");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> pantallaConsulta(this::reporteOrdenes, this::detalleOrden);
                case "2" -> pantallaConsulta(this::reporteTecnicos, this::detalleTecnico);
                case "3" -> pantallaConsulta(this::reporteConsumos, null);
                case "4" -> pantallaConsulta(this::reporteProveedores, this::detalleProveedor);
                case "5" -> consultaAlertas();
                case "6" -> pantallaConsulta(this::reporteFacturacion, null);
                case "7" -> pantallaConsulta(this::reporteCompras, null);
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void menuTrabajos(boolean desdeAdmin) {
        while (true) {
            cabecera("TRABAJOS POR TÉCNICO", "Elige un técnico para consultar sus órdenes y su historial");
            System.out.println("  1. Técnicos con órdenes activas");
            if (desdeAdmin) {
                System.out.println("  2. Técnicos sin órdenes activas [SOLO ADMIN]");
                System.out.println("  3. Seleccionar técnico de la lista completa");
            } else {
                System.out.println("  2. Trabajos del técnico seleccionado al iniciar sesión");
            }
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> pantallaTecnicosPorCarga(true, desdeAdmin);
                case "2" -> {
                    if (desdeAdmin) pantallaTecnicosPorCarga(false, true);
                    else if (tecnicoActivo != null) submenuTrabajosTecnico(tecnicos.get(tecnicoActivo), false);
                }
                case "3" -> {
                    if (desdeAdmin) {
                        Tecnico t = elegirTecnico(false);
                        if (t != null) submenuTrabajosTecnico(t, true);
                    } else aviso("Esta opción es exclusiva de administración.");
                }
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void pantallaTecnicosPorCarga(boolean conOrdenes, boolean desdeAdmin) {
        if (!conOrdenes && !desdeAdmin) {
            aviso("Solo el administrador puede consultar técnicos sin órdenes activas.");
            return;
        }
        while (true) {
            List<Tecnico> lista = tecnicos.values().stream()
                    .filter(t -> t.activo)
                    .filter(t -> tieneOrdenActiva(t.id) == conOrdenes)
                    .toList();
            cabecera(conOrdenes ? "TÉCNICOS CON ÓRDENES ACTIVAS" : "TÉCNICOS SIN ÓRDENES ACTIVAS",
                    "Solo personal activo; 'sin órdenes' no significa disponibilidad laboral garantizada");
            if (lista.isEmpty()) System.out.println("  (No hay técnicos en esta categoría)");
            for (int i = 0; i < lista.size(); i++) {
                Tecnico t = lista.get(i);
                System.out.printf("  %d. %-22s | %d órdenes activas%n", i + 1, t.nombre, cantidadActivas(t.id));
            }
            System.out.println("  0. Volver             M. Menú principal");
            String x = entrada("Selecciona un técnico por número");
            if (x.equals("0")) return;
            Integer elegido = convertirNumero(x);
            if (elegido == null || elegido < 1 || elegido > lista.size()) { aviso("Número fuera de la lista."); continue; }
            submenuTrabajosTecnico(lista.get(elegido - 1), desdeAdmin);
        }
    }

    private void submenuTrabajosTecnico(Tecnico t, boolean desdeAdmin) {
        while (true) {
            cabecera("TRABAJOS DE " + t.nombre.toUpperCase(Locale.ROOT),
                    "Activas: " + cantidadActivas(t.id) + " | Históricas: " + cantidadHistoricas(t.id));
            System.out.println("  1. Órdenes activas (Pendiente / En proceso)");
            System.out.println("  2. Historial de trabajos atendidos");
            System.out.println("  3. Ver ficha del técnico");
            System.out.println("  0. Cambiar de técnico   M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> seleccionarOrdenDeTecnico(t, true, desdeAdmin);
                case "2" -> seleccionarOrdenDeTecnico(t, false, desdeAdmin);
                case "3" -> pantallaConsulta(() -> imprimirTecnico(t), null);
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void seleccionarOrdenDeTecnico(Tecnico t, boolean activas, boolean desdeAdmin) {
        while (true) {
            List<Orden> lista = ordenes.values().stream()
                    .filter(o -> o.tecnicos.contains(t.id) && o.estado.esActiva() == activas)
                    .sorted(Comparator.comparingInt(o -> o.id)).toList();
            cabecera(activas ? "ÓRDENES ACTIVAS - " + t.nombre : "HISTORIAL - " + t.nombre,
                    "Selecciona una orden para ver sus detalles");
            if (lista.isEmpty()) System.out.println("  No hay órdenes en esta sección.");
            for (int i = 0; i < lista.size(); i++) {
                Orden o = lista.get(i);
                System.out.printf("  %d. #%d | %s | %s%n", i + 1, o.id, placa(o), o.estado.etiqueta);
            }
            System.out.println("  0. Volver             M. Menú principal");
            String op = entrada("Número de orden");
            if (op.equals("0")) return;
            Integer n = convertirNumero(op);
            if (n == null || n < 1 || n > lista.size()) { aviso("Selecciona un número válido."); continue; }
            detalleOrdenConOpciones(lista.get(n - 1), desdeAdmin, t.id);
        }
    }

    private void detalleOrdenConOpciones(Orden orden, boolean desdeAdmin, int tecnicoContexto) {
        while (true) {
            cabecera("ORDEN #" + orden.id, "Detalle de trabajo - " + placa(orden));
            imprimirOrden(orden);
            System.out.println("\n  ¿Qué deseas hacer ahora?");
            if (!desdeAdmin && tecnicoActivo != null && tecnicoActivo == tecnicoContexto
                    && orden.estado == Estado.EN_PROCESO && orden.tecnicos.contains(tecnicoActivo)) {
                System.out.println("  1. FINALIZAR TRABAJO (En proceso -> Completado)");
            }
            if (desdeAdmin) System.out.println("  2. Registrar horas ADICIONALES a esta asignación");
            System.out.println("  0. Volver a órdenes    M. Panel principal");
            String op = entrada("Opción");
            if (op.equals("0")) return;
            if (op.equals("1") && !desdeAdmin && tecnicoActivo != null && tecnicoActivo == tecnicoContexto
                    && orden.estado == Estado.EN_PROCESO && orden.tecnicos.contains(tecnicoActivo)) {
                finalizarComoTecnico(orden);
            } else if (op.equals("2") && desdeAdmin) {
                acumularHorasPara(orden, tecnicoContexto);
            } else aviso("Esa operación no corresponde a tu rol o al estado de esta orden.");
        }
    }

    private void crearOrden() {
        cabecera("CREAR ORDEN DE SERVICIO", "Una orden nueva queda Pendiente y sin técnicos asignados");
        listarVehiculos();
        Integer vehiculoId = pedirId("ID del vehículo (0 cancela)", vehiculos);
        if (vehiculoId == null) return;
        System.out.println("  1. Preventivo     2. Correctivo");
        String tipoOp = entrada("Tipo de mantenimiento");
        String tipo = tipoOp.equals("1") ? "Preventivo" : tipoOp.equals("2") ? "Correctivo" : null;
        if (tipo == null) { aviso("Tipo incorrecto: operación cancelada."); return; }
        String problema = textoObligatorio("Descripción del problema o trabajo");
        if (!confirmar("Crear una orden nueva para " + vehiculos.get(vehiculoId).placa + "?")) return;
        Orden o = new Orden(siguienteOrden++, vehiculoId, tipo, problema);
        ordenes.put(o.id, o);
        exito("Orden #" + o.id + " creada. Estado: Pendiente. Aún sin técnico asignado.");
        postOperacion();
    }

    private void iniciarOrden() {
        cabecera("INICIAR ORDEN", "ADMIN | Pendiente -> En proceso");
        listarOrdenes(Estado.PENDIENTE);
        Orden o = pedirOrden("ID de la orden pendiente (0 cancela)");
        if (o == null) return;
        if (o.estado != Estado.PENDIENTE) { aviso("Solo pueden iniciarse órdenes Pendientes."); return; }
        if (o.tecnicos.isEmpty()) { aviso("Primero asigna al menos un técnico; evita iniciar trabajo sin responsable."); return; }
        if (confirmar("Iniciar orden #" + o.id + " y pasarla a En proceso?")) {
            o.estado = Estado.EN_PROCESO;
            exito("Orden #" + o.id + " ahora está En proceso.");
            postOperacion();
        }
    }

    private void finalizarComoTecnico(Orden o) {
        if (rol != Rol.TECNICO || tecnicoActivo == null || !o.tecnicos.contains(tecnicoActivo)) {
            aviso("Solo el técnico seleccionado, asignado a la orden, puede finalizarla en esta demo."); return;
        }
        if (o.estado != Estado.EN_PROCESO) { aviso("La orden debe estar En proceso."); return; }
        for (Map.Entry<Integer, Integer> r : o.reservas.entrySet()) {
            if (stockFisico(r.getKey()) < r.getValue()) {
                aviso("Stock insuficiente para consumir: " + repuestos.get(r.getKey()).nombre); return;
            }
        }
        if (!confirmar("¿Confirmas terminar la orden #" + o.id + "? Consumirá las reservas y pasará a Completado.")) return;
        // Primero se preparan los retiros; la demo no emite SQL ni manipula facturas.
        for (Map.Entry<Integer, Integer> r : o.reservas.entrySet()) {
            int pendiente = r.getValue();
            for (Lote lote : lotes.values()) {
                if (lote.repuestoId == r.getKey() && pendiente > 0) {
                    int consumo = Math.min(pendiente, lote.cantidadActual);
                    lote.cantidadActual -= consumo;
                    pendiente -= consumo;
                }
            }
            o.consumos.put(r.getKey(), r.getValue());
        }
        o.reservas.clear();
        o.estado = Estado.COMPLETADO;
        exito("Orden #" + o.id + " COMPLETADA. El consumo de inventario ha sido simulado.");
        postOperacion();
    }

    private void asignarTecnico() {
        cabecera("ASIGNAR TÉCNICO", "ADMIN | evita asignaciones duplicadas");
        listarOrdenesActivas();
        Orden o = pedirOrden("ID de la orden abierta (0 cancela)");
        if (o == null) return;
        if (!o.estado.esActiva()) { aviso("La orden ya está completada o cerrada."); return; }
        Tecnico t = elegirTecnico(true);
        if (t == null) return;
        if (o.tecnicos.contains(t.id)) { aviso(t.nombre + " ya está asignado a esa orden."); return; }
        if (confirmar("Asignar a " + t.nombre + " a la orden #" + o.id + "?")) {
            asignarDemo(o, t.id, dinero("0"));
            exito("Asignación creada. El listado de técnicos con trabajo se ha actualizado.");
            postOperacion();
        }
    }

    private void retirarTecnico() {
        cabecera("RETIRAR ASIGNACIÓN", "ADMIN | simulación de cambio sensible");
        listarOrdenesActivas();
        Orden o = pedirOrden("ID de la orden (0 cancela)");
        if (o == null) return;
        if (!o.estado.esActiva()) { aviso("Solo se modifican asignaciones de órdenes abiertas."); return; }
        if (o.tecnicos.isEmpty()) { aviso("No tiene técnicos asignados."); return; }
        for (Integer id : o.tecnicos) System.out.println("  #" + id + " " + tecnicos.get(id).nombre);
        Integer id = numeroPositivo("ID de técnico a retirar (0 cancela)");
        if (id == null) return;
        if (!o.tecnicos.contains(id)) { aviso("Ese técnico no participa en la orden."); return; }
        if (o.horas.getOrDefault(id, BigDecimal.ZERO).signum() > 0) {
            aviso("Ya hay horas registradas. La demo NO borra esta asignación para no perder su historial."); return;
        }
        if (confirmar("Retirar asignación de " + tecnicos.get(id).nombre + "?")) {
            o.tecnicos.remove(id); o.horas.remove(id);
            exito("Asignación retirada. Se actualizaron las listas de técnicos.");
            postOperacion();
        }
    }

    private void acumularHoras() {
        cabecera("REGISTRAR HORAS ADICIONALES", "ADMIN | comportamiento previsto; compañero aún lo desarrolla");
        Orden o = pedirOrden("ID de la orden (0 cancela)");
        if (o == null) return;
        if (o.tecnicos.isEmpty()) { aviso("La orden no tiene técnicos asignados."); return; }
        for (Integer id : o.tecnicos) System.out.printf("  #%d %s | acumulado: %s h%n", id, tecnicos.get(id).nombre,
                o.horas.getOrDefault(id, BigDecimal.ZERO).toPlainString());
        Integer id = numeroPositivo("ID del técnico (0 cancela)");
        if (id == null) return;
        if (!o.tecnicos.contains(id)) { aviso("Técnico no asignado a esa orden."); return; }
        acumularHorasPara(o, id);
    }

    private void acumularHorasPara(Orden orden, int tecnicoId) {
        if (rol != Rol.ADMINISTRADOR) { aviso("Solo el administrador registra horas."); return; }
        if (!orden.estado.esActiva()) { aviso("La orden está cerrada; no se pueden registrar horas nuevas."); return; }
        BigDecimal previas = orden.horas.getOrDefault(tecnicoId, dinero("0"));
        BigDecimal nuevas = decimalNoNegativo("Horas adicionales de hoy (por ejemplo 3 o 1.50)", false);
        if (nuevas == null) return;
        if (nuevas.signum() == 0 || previas.add(nuevas).compareTo(dinero("999.99")) > 0) {
            aviso("Horas adicionales deben ser > 0 y el total <= 999.99."); return;
        }
        System.out.printf("  Antes: %s h | Ahora añades: %s h | Total esperado: %s h%n",
                previas.toPlainString(), nuevas.toPlainString(), previas.add(nuevas).toPlainString());
        if (confirmar("¿Guardar las horas ADICIONALES? Si repites la operación, se sumarían otra vez.")) {
            orden.horas.put(tecnicoId, previas.add(nuevas));
            exito("Horas acumuladas: " + orden.horas.get(tecnicoId).toPlainString() + " h.");
            postOperacion();
        }
    }

    private void gestionarReservas() {
        while (true) {
            cabecera("GESTIONAR RESERVAS", "ADMIN | la reserva no descuenta aún el stock físico");
            System.out.println("  1. Reservar o ajustar una cantidad TOTAL para una orden");
            System.out.println("  2. Liberar reserva de una orden");
            System.out.println("  3. Consultar reservas de una orden");
            System.out.println("  0. Volver             M. Menú principal");
            String op = entrada("Opción");
            if (op.equals("0")) return;
            if (!op.equals("1") && !op.equals("2") && !op.equals("3")) { aviso("Opción no válida."); continue; }
            Orden o = pedirOrden("ID de la orden (0 cancela)");
            if (o == null) continue;
            if (op.equals("3")) { pantallaConsulta(() -> imprimirReservas(o), null); continue; }
            if (!o.estado.esActiva()) { aviso("No pueden cambiarse reservas de órdenes cerradas."); continue; }
            if (op.equals("2")) {
                imprimirReservas(o);
                Integer r = pedirId("ID de repuesto a liberar (0 cancela)", repuestos);
                if (r == null) continue;
                if (!o.reservas.containsKey(r)) { aviso("La orden no tiene ese repuesto reservado."); continue; }
                if (confirmar("¿Liberar la reserva de " + repuestos.get(r).nombre + "?")) {
                    o.reservas.remove(r); o.preciosHistoricos.remove(r);
                    exito("Reserva liberada en memoria."); postOperacion();
                }
                continue;
            }
            consultaRepuestosResumen();
            Integer r = pedirId("ID del repuesto (0 cancela)", repuestos);
            if (r == null) continue;
            Integer cantidad = numeroPositivo("Cantidad TOTAL deseada (0 cancela)");
            if (cantidad == null) continue;
            int disponibles = stockDisponible(r) + o.reservas.getOrDefault(r, 0);
            if (cantidad > disponibles) { aviso("Stock disponible insuficiente. Máximo para esta orden: " + disponibles); continue; }
            if (confirmar("¿Reservar " + cantidad + " unidades de " + repuestos.get(r).nombre + " para orden #" + o.id + "?")) {
                o.reservas.put(r, cantidad);
                o.preciosHistoricos.putIfAbsent(r, repuestos.get(r).precioVenta);
                exito("Reserva simulada guardada. Stock físico no se descuenta hasta completar la orden.");
                postOperacion();
            }
        }
    }

    private void solicitarRepuesto() {
        if (rol != Rol.TECNICO || tecnicoActivo == null) return;
        cabecera("SOLICITUD DE REPUESTO", "FUNCIÓN FUTURA: solo existe en esta simulación");
        List<Orden> candidatas = ordenes.values().stream().filter(o -> o.estado.esActiva() && o.tecnicos.contains(tecnicoActivo)).toList();
        if (candidatas.isEmpty()) { aviso("El técnico seleccionado no tiene órdenes activas asignadas."); return; }
        for (Orden o : candidatas) System.out.printf("  #%d | %s | %s%n", o.id, placa(o), o.estado.etiqueta);
        Orden orden = pedirOrden("ID de la orden propia (0 cancela)");
        if (orden == null) return;
        if (!candidatas.contains(orden)) { aviso("Solo puedes solicitar para una orden asignada a tu técnico seleccionado."); return; }
        consultaRepuestosResumen();
        Integer repuestoId = pedirId("ID de repuesto (0 cancela)", repuestos);
        if (repuestoId == null) return;
        Integer cantidad = numeroPositivo("Cantidad solicitada (0 cancela)");
        if (cantidad == null) return;
        if (confirmar("Enviar solicitud ficticia al administrador?")) {
            Solicitud sol = new Solicitud(siguienteSolicitud++, orden.id, tecnicoActivo, repuestoId, cantidad);
            solicitudes.put(sol.id, sol);
            exito("Solicitud DEMO #" + sol.id + " registrada. Pendiente de atención del administrador.");
            postOperacion();
        }
    }

    private void gestionarSolicitudes() {
        while (true) {
            cabecera("SOLICITUDES DE REPUESTOS", "MEJORA FUTURA | la app real aún no tiene este flujo");
            for (Solicitud s : solicitudes.values()) {
                System.out.printf("  #%d | Orden #%d | %s | %s x%d | %s%n", s.id, s.ordenId,
                        tecnicos.get(s.tecnicoId).nombre, repuestos.get(s.repuestoId).nombre, s.cantidad, s.estado);
            }
            if (solicitudes.isEmpty()) System.out.println("  No hay solicitudes registradas en esta ejecución.");
            System.out.println("  1. Atender solicitud (crear reserva si hay stock)");
            System.out.println("  2. Rechazar solicitud");
            System.out.println("  0. Volver             M. Menú principal");
            String op = entrada("Opción");
            if (op.equals("0")) return;
            if (!op.equals("1") && !op.equals("2")) { aviso("Opción no válida."); continue; }
            Integer id = pedirId("ID de solicitud (0 cancela)", solicitudes);
            if (id == null) continue;
            Solicitud s = solicitudes.get(id);
            if (s.estado != EstadoSolicitud.PENDIENTE) { aviso("Esta solicitud ya fue resuelta."); continue; }
            Orden o = ordenes.get(s.ordenId);
            if (op.equals("1")) {
                if (o == null || !o.estado.esActiva()) { aviso("La orden ya no admite reservas."); continue; }
                int total = o.reservas.getOrDefault(s.repuestoId, 0) + s.cantidad;
                if (total > stockDisponible(s.repuestoId) + o.reservas.getOrDefault(s.repuestoId, 0)) {
                    aviso("No hay stock para atender esta solicitud."); continue;
                }
                if (confirmar("Atender solicitud y añadir cantidad reservada?")) {
                    o.reservas.put(s.repuestoId, total);
                    o.preciosHistoricos.putIfAbsent(s.repuestoId, repuestos.get(s.repuestoId).precioVenta);
                    s.estado = EstadoSolicitud.ATENDIDA;
                    exito("Solicitud atendida y stock comprometido en memoria."); postOperacion();
                }
            } else if (confirmar("Rechazar esta solicitud ficticia?")) {
                s.estado = EstadoSolicitud.RECHAZADA;
                exito("Solicitud rechazada."); postOperacion();
            }
        }
    }

    private void recibirLote() {
        cabecera("RECEPCIÓN DE LOTE", "Operación de demostración con costo histórico de adquisición");
        listarProveedores();
        Integer proveedorId = pedirId("ID de proveedor (0 cancela)", proveedores);
        if (proveedorId == null) return;
        if (!proveedores.get(proveedorId).activo) { aviso("No se reciben lotes de un proveedor desactivado."); return; }
        consultaRepuestosResumen();
        Integer repuestoId = pedirId("ID de repuesto (0 cancela)", repuestos);
        if (repuestoId == null) return;
        String codigo = textoObligatorio("Código único del lote").toUpperCase(Locale.ROOT);
        if (lotes.values().stream().anyMatch(l -> l.codigo.equalsIgnoreCase(codigo))) { aviso("Ya existe un lote con ese código."); return; }
        Integer cantidad = numeroPositivo("Unidades recibidas (0 cancela)");
        if (cantidad == null) return;
        BigDecimal ofertado = proveedores.get(proveedorId).costoOfertado.get(repuestoId);
        if (ofertado != null) System.out.println("  Costo actualmente ofertado (referencia): " + pesos(ofertado));
        System.out.println("  Nota: el costo de este lote será histórico, independiente del ofertado actual.");
        BigDecimal costo = decimalNoNegativo("Costo unitario de adquisición (COP)", true);
        if (costo == null) return;
        if (!confirmar("Registrar lote " + codigo + " con " + cantidad + " unidades?")) return;
        Lote lote = new Lote(siguienteLote++, proveedorId, repuestoId, cantidad, costo, codigo);
        lotes.put(lote.id, lote);
        exito("Lote #" + lote.id + " recibido. Stock actualizado en memoria: " + stockFisico(repuestoId));
        postOperacion();
    }

    private void facturar() {
        cabecera("FACTURAR ORDEN", "ADMIN | COMPLETADO -> FACTURADO");
        listarOrdenes(Estado.COMPLETADO);
        Orden orden = pedirOrden("ID de orden completada (0 cancela)");
        if (orden == null) return;
        if (orden.estado != Estado.COMPLETADO || orden.facturaId != null) { aviso("Se requiere orden Completada y aún sin factura."); return; }
        BigDecimal repuestosTotal = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Integer, Integer> e : orden.consumos.entrySet()) {
            repuestosTotal = repuestosTotal.add(orden.preciosHistoricos.getOrDefault(e.getKey(), BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(e.getValue())));
        }
        System.out.println("  Repuestos a precio histórico de reserva (demo): " + pesos(repuestosTotal));
        BigDecimal manoObra = decimalNoNegativo("Importe de mano de obra a cobrar (COP)", true);
        if (manoObra == null) return;
        System.out.println("  La tasa impositiva del proyecto NO está confirmada.");
        BigDecimal porcentaje = decimalNoNegativo("Tasa ilustrativa de impuesto % (0 si no se define)", true);
        if (porcentaje == null) return;
        if (porcentaje.compareTo(new BigDecimal("100")) > 0) { aviso("La tasa debe estar entre 0% y 100%."); return; }
        BigDecimal impuestos = repuestosTotal.add(manoObra).multiply(porcentaje)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal total = repuestosTotal.add(manoObra).add(impuestos);
        System.out.println("  RESUMEN: repuestos " + pesos(repuestosTotal) + " | mano de obra " + pesos(manoObra)
                + " | impuesto " + pesos(impuestos) + " | TOTAL " + pesos(total));
        if (!confirmar("¿Confirmar facturación ficticia de orden #" + orden.id + "?")) return;
        Factura factura = new Factura(siguienteFactura++, orden.id, repuestosTotal, manoObra, impuestos);
        facturas.put(factura.id, factura);
        orden.facturaId = factura.id;
        orden.estado = Estado.FACTURADO;
        exito("Factura DEMO #" + factura.id + " emitida. Orden #" + orden.id + " ahora Facturada.");
        postOperacion();
    }

    private void entregar() {
        cabecera("ENTREGAR VEHÍCULO", "ADMIN | FACTURADO -> ENTREGADO");
        listarOrdenes(Estado.FACTURADO);
        Orden o = pedirOrden("ID de orden facturada (0 cancela)");
        if (o == null) return;
        if (o.estado != Estado.FACTURADO || o.facturaId == null) {
            aviso("Solo se entrega una orden Facturada con factura registrada."); return;
        }
        if (confirmar("¿Confirmar entrega del vehículo " + placa(o) + " al cliente?")) {
            o.estado = Estado.ENTREGADO;
            exito("Orden #" + o.id + " Entregada. Historial actualizado en memoria.");
            postOperacion();
        }
    }

    private void gestionClientes() {
        while (true) {
            cabecera("CRUD DE CLIENTES", "ADMIN | alta, consulta, modificación y eliminación restringida");
            System.out.println("  1. Registrar cliente  2. Listar y consultar  3. Modificar  4. Eliminar");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> crearCliente();
                case "2" -> pantallaConsulta(this::listarClientes, this::detalleCliente);
                case "3" -> {
                    listarClientes(); Integer id = pedirId("ID a modificar (0 cancela)", clientes);
                    if (id == null) break;
                    Cliente c = clientes.get(id);
                    String nombre = textoObligatorio("Nuevo nombre");
                    String telefono = textoObligatorio("Nuevo teléfono");
                    if (confirmar("Actualizar datos del cliente #" + id + "?")) {
                        c.nombre = nombre; c.telefono = telefono; exito("Cliente actualizado."); postOperacion();
                    }
                }
                case "4" -> {
                    listarClientes(); Integer id = pedirId("ID a eliminar (0 cancela)", clientes);
                    if (id == null) break;
                    if (vehiculos.values().stream().anyMatch(v -> v.clienteId == id)) {
                        aviso("No se puede eliminar: existen vehículos asociados. Regla de integridad simulada."); break;
                    }
                    if (confirmar("¿Eliminar definitivamente a " + clientes.get(id).nombre + "?")) {
                        clientes.remove(id); exito("Cliente eliminado de memoria."); postOperacion();
                    }
                }
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void crearCliente() {
        cabecera("REGISTRAR CLIENTE", "Datos ficticios editables durante esta sesión");
        String nombre = textoObligatorio("Nombre completo");
        String documento = textoObligatorio("Documento de identidad");
        if (clientes.values().stream().anyMatch(c -> c.documento.equalsIgnoreCase(documento))) {
            aviso("Ya existe un cliente con ese documento."); return;
        }
        String telefono = textoObligatorio("Teléfono");
        if (confirmar("¿Registrar a " + nombre + "?")) {
            Cliente c = new Cliente(siguienteCliente++, nombre, documento, telefono);
            clientes.put(c.id, c); exito("Cliente creado con ID #" + c.id); postOperacion();
        }
    }

    private void gestionVehiculos() {
        while (true) {
            cabecera("CRUD DE VEHÍCULOS", "ADMIN | las placas no pueden duplicarse");
            System.out.println("  1. Registrar  2. Listar / consultar  3. Cambiar descripción  4. Eliminar");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> crearVehiculo();
                case "2" -> pantallaConsulta(this::listarVehiculos, this::detalleVehiculo);
                case "3" -> {
                    listarVehiculos(); Integer id = pedirId("ID a modificar (0 cancela)", vehiculos);
                    if (id == null) break;
                    String descripcion = textoObligatorio("Nueva descripción");
                    if (confirmar("¿Actualizar descripción?")) {
                        vehiculos.get(id).descripcion = descripcion; exito("Vehículo actualizado."); postOperacion();
                    }
                }
                case "4" -> {
                    listarVehiculos(); Integer id = pedirId("ID a eliminar (0 cancela)", vehiculos);
                    if (id == null) break;
                    if (ordenes.values().stream().anyMatch(o -> o.vehiculoId == id)) {
                        aviso("No se puede eliminar: el vehículo tiene historial de órdenes."); break;
                    }
                    if (confirmar("¿Eliminar vehículo " + vehiculos.get(id).placa + "?")) {
                        vehiculos.remove(id); exito("Vehículo eliminado."); postOperacion();
                    }
                }
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void crearVehiculo() {
        cabecera("REGISTRAR VEHÍCULO", "Debe pertenecer a un cliente existente");
        listarClientes();
        Integer clienteId = pedirId("ID del cliente (0 cancela)", clientes);
        if (clienteId == null) return;
        String placa = textoObligatorio("Placa").toUpperCase(Locale.ROOT);
        if (vehiculos.values().stream().anyMatch(v -> v.placa.equalsIgnoreCase(placa))) {
            aviso("La placa ya está registrada."); return;
        }
        String descripcion = textoObligatorio("Marca / modelo / año");
        if (confirmar("¿Registrar vehículo " + placa + "?")) {
            Vehiculo v = new Vehiculo(siguienteVehiculo++, clienteId, placa, descripcion);
            vehiculos.put(v.id, v); exito("Vehículo registrado con ID #" + v.id); postOperacion();
        }
    }

    private void gestionTecnicos() {
        while (true) {
            cabecera("GESTIÓN DE TÉCNICOS", "ADMIN | desactivar preserva el historial");
            System.out.println("  1. Registrar  2. Listar / consultar  3. Editar  4. Activar / desactivar");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> {
                    String nombre = textoObligatorio("Nombre");
                    String especialidad = textoObligatorio("Especialidad");
                    if (confirmar("¿Registrar nuevo técnico?")) {
                        Tecnico t = new Tecnico(siguienteTecnico++, nombre, especialidad);
                        tecnicos.put(t.id, t); exito("Técnico #" + t.id + " registrado."); postOperacion();
                    }
                }
                case "2" -> pantallaConsulta(this::listarTodosTecnicos, this::detalleTecnico);
                case "3" -> {
                    listarTodosTecnicos(); Integer id = pedirId("ID a editar (0 cancela)", tecnicos);
                    if (id == null) break;
                    String nombre = textoObligatorio("Nuevo nombre");
                    String especialidad = textoObligatorio("Nueva especialidad");
                    if (confirmar("¿Actualizar técnico?")) {
                        tecnicos.get(id).nombre = nombre; tecnicos.get(id).especialidad = especialidad;
                        exito("Técnico actualizado."); postOperacion();
                    }
                }
                case "4" -> {
                    listarTodosTecnicos(); Integer id = pedirId("ID a activar/desactivar (0 cancela)", tecnicos);
                    if (id == null) break;
                    Tecnico t = tecnicos.get(id);
                    if (t.activo && tieneOrdenActiva(id)) {
                        aviso("No desactives un técnico con órdenes activas. Primero gestiona sus asignaciones."); break;
                    }
                    if (confirmar((t.activo ? "¿Desactivar" : "¿Reactivar") + " a " + t.nombre + "?")) {
                        t.activo = !t.activo; exito("Nuevo estado: " + (t.activo ? "Activo" : "Inactivo")); postOperacion();
                    }
                }
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void gestionProveedores() {
        while (true) {
            cabecera("GESTIÓN DE PROVEEDORES", "ADMIN | CRUD, catálogo ofertado y evaluaciones");
            System.out.println("  1. Registrar proveedor  2. Consultar  3. Modificar  4. Desactivar/eliminar");
            System.out.println("  5. Añadir / cambiar costo ofertado  6. Evaluar recepción de lote");
            System.out.println("  7. Consultar evaluaciones y promedios");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> {
                    String nombre = textoObligatorio("Nombre del proveedor");
                    String contacto = textoObligatorio("Contacto");
                    if (confirmar("¿Registrar proveedor?")) {
                        Proveedor p = new Proveedor(siguienteProveedor++, nombre, contacto);
                        proveedores.put(p.id, p); exito("Proveedor #" + p.id + " registrado."); postOperacion();
                    }
                }
                case "2" -> pantallaConsulta(this::listarProveedores, this::detalleProveedor);
                case "3" -> {
                    listarProveedores(); Integer id = pedirId("ID a modificar (0 cancela)", proveedores);
                    if (id == null) break;
                    String nombre = textoObligatorio("Nuevo nombre");
                    String contacto = textoObligatorio("Nuevo contacto");
                    if (confirmar("¿Actualizar proveedor?")) {
                        proveedores.get(id).nombre = nombre; proveedores.get(id).contacto = contacto;
                        exito("Proveedor actualizado."); postOperacion();
                    }
                }
                case "4" -> {
                    listarProveedores(); Integer id = pedirId("ID a desactivar/eliminar (0 cancela)", proveedores);
                    if (id == null) break;
                    Proveedor p = proveedores.get(id);
                    boolean conHistorico = lotes.values().stream().anyMatch(l -> l.proveedorId == id);
                    if (conHistorico) {
                        if (confirmar("Tiene lotes históricos. ¿Desactivar sin borrar?")) {
                            p.activo = false; exito("Proveedor desactivado; historia preservada."); postOperacion();
                        }
                    } else if (confirmar("¿Eliminar este proveedor sin lotes?")) {
                        proveedores.remove(id); exito("Proveedor eliminado."); postOperacion();
                    }
                }
                case "5" -> {
                    listarProveedores(); Integer pid = pedirId("Proveedor (0 cancela)", proveedores);
                    if (pid == null) break;
                    if (!proveedores.get(pid).activo) { aviso("Proveedor inactivo."); break; }
                    consultaRepuestosResumen(); Integer rid = pedirId("Repuesto (0 cancela)", repuestos);
                    if (rid == null) break;
                    BigDecimal costo = decimalNoNegativo("Nuevo costo ACTUALMENTE OFERTADO (COP)", true);
                    if (costo == null) break;
                    if (confirmar("¿Actualizar oferta? Los lotes anteriores no cambiarán.")) {
                        proveedores.get(pid).costoOfertado.put(rid, costo);
                        exito("Costo ofertado actualizado; adquisición histórica intacta."); postOperacion();
                    }
                }
                case "6" -> {
                    consultaLotesResumen(); Integer lid = pedirId("Lote recibido (0 cancela)", lotes);
                    if (lid == null) break;
                    Integer calidad = rango("Calidad (1-5)", 1, 5);
                    Integer puntualidad = rango("Puntualidad (1-5)", 1, 5);
                    String obs = entrada("Observaciones (opcional)");
                    if (confirmar("¿Registrar evaluación de este lote?")) {
                        Lote lote = lotes.get(lid);
                        Evaluacion e = new Evaluacion(siguienteEvaluacion++, lote.proveedorId, lid, calidad, puntualidad, obs);
                        evaluaciones.put(e.id, e); exito("Evaluación #" + e.id + " registrada."); postOperacion();
                    }
                }
                case "7" -> pantallaConsulta(this::reporteProveedores, this::detalleProveedor);
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void gestionRepuestos() {
        while (true) {
            cabecera("CATÁLOGO DE REPUESTOS", "ADMIN | stock físico proviene de lotes, NO de un campo editable");
            System.out.println("  1. Registrar  2. Consultar  3. Modificar precio / mínimo  4. Eliminar");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> {
                    String nombre = textoObligatorio("Nombre del repuesto");
                    BigDecimal precio = decimalNoNegativo("Precio de venta (COP)", true);
                    if (precio == null) break;
                    Integer minimo = enteroNoNegativo("Stock mínimo", false);
                    if (minimo == null) break;
                    if (confirmar("¿Añadir repuesto?")) {
                        Repuesto r = new Repuesto(siguienteRepuesto++, nombre, precio, minimo);
                        repuestos.put(r.id, r); exito("Repuesto #" + r.id + " creado sin stock físico."); postOperacion();
                    }
                }
                case "2" -> consultaRepuestos();
                case "3" -> {
                    consultaRepuestosResumen(); Integer id = pedirId("ID a modificar (0 cancela)", repuestos);
                    if (id == null) break;
                    BigDecimal precio = decimalNoNegativo("Nuevo precio VIGENTE de venta (COP)", true);
                    if (precio == null) break;
                    Integer minimo = enteroNoNegativo("Nuevo stock mínimo", false);
                    if (minimo == null) break;
                    if (confirmar("¿Actualizar sin cambiar precios históricos de órdenes?")) {
                        repuestos.get(id).precioVenta = precio; repuestos.get(id).minimo = minimo;
                        exito("Catálogo actualizado; históricos preservados."); postOperacion();
                    }
                }
                case "4" -> {
                    consultaRepuestosResumen(); Integer id = pedirId("ID a eliminar (0 cancela)", repuestos);
                    if (id == null) break;
                    boolean usado = lotes.values().stream().anyMatch(l -> l.repuestoId == id)
                            || ordenes.values().stream().anyMatch(o -> o.reservas.containsKey(id) || o.consumos.containsKey(id))
                            || proveedores.values().stream().anyMatch(p -> p.costoOfertado.containsKey(id));
                    if (usado) { aviso("No se puede eliminar: repuesto referenciado por lotes, proveedores u órdenes."); break; }
                    if (confirmar("¿Eliminar repuesto?")) {
                        repuestos.remove(id); exito("Repuesto eliminado."); postOperacion();
                    }
                }
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void consultaOrdenesPorEstado() {
        while (true) {
            cabecera("CONSULTA DE ÓRDENES", "Selecciona qué deseas ver");
            System.out.println("  1. Todas   2. Pendientes   3. En proceso   4. Completadas");
            System.out.println("  5. Facturadas   6. Entregadas");
            System.out.println("  0. Volver             M. Menú principal");
            String op = entrada("Opción");
            if (op.equals("0")) return;
            Estado estado = switch (op) {
                case "2" -> Estado.PENDIENTE; case "3" -> Estado.EN_PROCESO;
                case "4" -> Estado.COMPLETADO; case "5" -> Estado.FACTURADO;
                case "6" -> Estado.ENTREGADO; default -> null;
            };
            if (!op.equals("1") && estado == null) { aviso("Opción no válida."); continue; }
            pantallaConsulta(() -> listarOrdenes(estado), this::detalleOrden);
        }
    }

    private void consultaOrdenes(Estado estado) { pantallaConsulta(() -> listarOrdenes(estado), this::detalleOrden); }
    private void consultaFacturas() { pantallaConsulta(this::listarFacturas, this::detalleFactura); }
    private void consultaRepuestos() { pantallaConsulta(this::consultaRepuestosResumen, this::detalleRepuesto); }
    private void consultaLotes() { pantallaConsulta(this::consultaLotesResumen, this::detalleLote); }
    private void consultaAlertas() { pantallaConsulta(this::mostrarAlertas, this::detalleRepuesto); }
    private void consultaHistorialVehiculos() { pantallaConsulta(this::listarHistorialVehiculos, this::detalleVehiculo); }
    private void consultarClientesVehiculos() {
        while (true) {
            cabecera("CONSULTAR CLIENTES Y VEHÍCULOS", "Técnico: solo lectura");
            System.out.println("  1. Listar clientes y consultar ficha");
            System.out.println("  2. Listar vehículos y consultar ficha");
            System.out.println("  0. Volver             M. Menú principal");
            switch (entrada("Opción")) {
                case "1" -> pantallaConsulta(this::listarClientes, this::detalleCliente);
                case "2" -> pantallaConsulta(this::listarVehiculos, this::detalleVehiculo);
                case "0" -> { return; }
                default -> aviso("Opción no válida.");
            }
        }
    }

    private void pantallaConsulta(Runnable mostrar, Runnable detalle) {
        while (true) {
            cabecera("RESULTADOS DE CONSULTA", "Puedes revisar los resultados antes de salir");
            mostrar.run();
            System.out.println("\n  ¿Qué deseas hacer ahora?");
            System.out.println("  1. Actualizar / repetir consulta");
            if (detalle != null) System.out.println("  2. Abrir detalle por ID");
            System.out.println("  0. Regresar a opciones anteriores");
            System.out.println("  M. Ir al menú principal");
            String op = entrada("Opción");
            if (op.equals("0")) return;
            if (op.equals("1")) continue;
            if (op.equals("2") && detalle != null) { detalle.run(); continue; }
            aviso("Opción no válida.");
        }
    }

    private void detalleOrden() {
        Orden o = pedirOrden("ID de orden a consultar (0 cancela)");
        if (o != null) { imprimirOrden(o); postLectura(); }
    }
    private void detalleCliente() {
        Integer id = pedirId("ID de cliente (0 cancela)", clientes);
        if (id == null) return;
        Cliente c = clientes.get(id);
        System.out.printf("  #%d %s | documento: %s | teléfono: %s%n", c.id, c.nombre, c.documento, c.telefono);
        System.out.println("  Vehículos:");
        vehiculos.values().stream().filter(v -> v.clienteId == c.id)
                .forEach(v -> System.out.println("  - " + v.placa + " " + v.descripcion));
        postLectura();
    }
    private void detalleVehiculo() {
        Integer id = pedirId("ID de vehículo (0 cancela)", vehiculos);
        if (id == null) return;
        Vehiculo v = vehiculos.get(id);
        System.out.printf("  #%d %s | %s | dueño: %s%n", v.id, v.placa, v.descripcion, clientes.get(v.clienteId).nombre);
        System.out.println("  Historial de órdenes:");
        ordenes.values().stream().filter(o -> o.vehiculoId == id)
                .forEach(o -> System.out.printf("  - #%d %s | %s%n", o.id, o.tipo, o.estado.etiqueta));
        postLectura();
    }
    private void detalleTecnico() {
        Integer id = pedirId("ID de técnico (0 cancela)", tecnicos);
        if (id != null) { imprimirTecnico(tecnicos.get(id)); postLectura(); }
    }
    private void detalleProveedor() {
        Integer id = pedirId("ID de proveedor (0 cancela)", proveedores);
        if (id == null) return;
        Proveedor p = proveedores.get(id);
        System.out.printf("  #%d %s | %s | %s%n", p.id, p.nombre, p.contacto, p.activo ? "Activo" : "Inactivo");
        System.out.println("  Ofertas actuales:");
        p.costoOfertado.forEach((rid, costo) -> System.out.println("  - " + repuestos.get(rid).nombre + ": " + pesos(costo)));
        System.out.println("  Evaluaciones por lote:");
        evaluaciones.values().stream().filter(e -> e.proveedorId == id)
                .forEach(e -> System.out.printf("  - Lote #%d | calidad %d/5 | puntualidad %d/5 | %s%n",
                        e.loteId, e.calidad, e.puntualidad, e.observaciones));
        postLectura();
    }
    private void detalleRepuesto() {
        Integer id = pedirId("ID de repuesto (0 cancela)", repuestos);
        if (id == null) return;
        Repuesto r = repuestos.get(id);
        System.out.printf("  #%d %s | venta: %s | físico: %d | reservado: %d | disponible: %d | mínimo: %d%n",
                r.id, r.nombre, pesos(r.precioVenta), stockFisico(id), stockReservado(id), stockDisponible(id), r.minimo);
        for (Lote l : lotes.values()) if (l.repuestoId == id)
            System.out.printf("  Lote %s | actual %d | costo histórico %s%n", l.codigo, l.cantidadActual, pesos(l.costoAdquisicion));
        postLectura();
    }
    private void detalleLote() {
        Integer id = pedirId("ID de lote (0 cancela)", lotes);
        if (id == null) return;
        Lote l = lotes.get(id);
        System.out.printf("  %s | %s | %s | recibidas %d | actuales %d | costo adquisición %s%n",
                l.codigo, proveedores.get(l.proveedorId).nombre, repuestos.get(l.repuestoId).nombre,
                l.cantidadOriginal, l.cantidadActual, pesos(l.costoAdquisicion));
        postLectura();
    }
    private void detalleFactura() {
        Integer id = pedirId("ID de factura (0 cancela)", facturas);
        if (id == null) return;
        Factura f = facturas.get(id);
        System.out.printf("  Factura #%d | orden #%d | fecha %s%n", f.id, f.ordenId, f.fecha);
        System.out.println("  Repuestos: " + pesos(f.repuestos) + " | mano de obra: " + pesos(f.manoObra));
        System.out.println("  Impuesto: " + pesos(f.impuestos) + " | TOTAL: " + pesos(f.total));
        postLectura();
    }

    private void imprimirOrden(Orden o) {
        Vehiculo v = vehiculos.get(o.vehiculoId);
        System.out.printf("  Orden #%d | %s | %s | %s%n", o.id, v.placa, o.tipo, o.estado.etiqueta);
        System.out.println("  Cliente: " + clientes.get(v.clienteId).nombre);
        System.out.println("  Descripción: " + o.problema);
        System.out.println("  Técnicos:");
        if (o.tecnicos.isEmpty()) System.out.println("    (Sin asignación)");
        for (Integer id : o.tecnicos) {
            System.out.printf("    - %s | %s h registradas%n", tecnicos.get(id).nombre,
                    o.horas.getOrDefault(id, dinero("0")).toPlainString());
        }
        System.out.println("  Reservas pendientes:");
        imprimirReservas(o);
        if (!o.consumos.isEmpty()) {
            System.out.println("  Repuestos consumidos:");
            o.consumos.forEach((id, cant) -> System.out.println("    - " + repuestos.get(id).nombre + " x" + cant));
        }
        if (o.facturaId != null) System.out.println("  Factura DEMO vinculada: #" + o.facturaId);
    }
    private void imprimirReservas(Orden o) {
        if (o.reservas.isEmpty()) System.out.println("    (Sin reservas activas)");
        for (Map.Entry<Integer, Integer> r : o.reservas.entrySet()) {
            System.out.printf("    - %s x%d | precio registrado %s%n", repuestos.get(r.getKey()).nombre,
                    r.getValue(), pesos(o.preciosHistoricos.get(r.getKey())));
        }
    }
    private void listarOrdenes(Estado estado) {
        List<Orden> lista = ordenes.values().stream().filter(o -> estado == null || o.estado == estado).toList();
        if (lista.isEmpty()) System.out.println("  No hay órdenes para este filtro.");
        for (Orden o : lista) {
            String encargados = o.tecnicos.isEmpty() ? "sin técnico" : o.tecnicos.stream()
                    .map(id -> tecnicos.get(id).nombre).collect(Collectors.joining(", "));
            System.out.printf("  #%d | %-7s | %-12s | %s | %s%n", o.id, placa(o), o.estado.etiqueta, encargados, o.problema);
        }
    }
    private void listarOrdenesActivas() {
        for (Orden o : ordenes.values()) if (o.estado.esActiva())
            System.out.printf("  #%d | %s | %s | %d técnicos%n", o.id, placa(o), o.estado.etiqueta, o.tecnicos.size());
    }
    private void listarClientes() {
        if (clientes.isEmpty()) System.out.println("  Sin clientes.");
        clientes.values().forEach(c -> System.out.printf("  #%d | %-24s | doc. %s%n", c.id, c.nombre, c.documento));
    }
    private void listarVehiculos() {
        if (vehiculos.isEmpty()) System.out.println("  Sin vehículos.");
        vehiculos.values().forEach(v -> System.out.printf("  #%d | %-8s | %-25s | %s%n",
                v.id, v.placa, v.descripcion, clientes.get(v.clienteId).nombre));
    }
    private void listarTodosTecnicos() {
        tecnicos.values().forEach(t -> System.out.printf("  #%d | %-21s | %s | órdenes activas: %d%n",
                t.id, t.nombre, t.activo ? "Activo" : "Inactivo", cantidadActivas(t.id)));
    }
    private void imprimirTecnico(Tecnico t) {
        System.out.printf("  #%d %s | %s | %s%n", t.id, t.nombre, t.especialidad, t.activo ? "Activo" : "Inactivo");
        System.out.printf("  Órdenes activas: %d | Historial de órdenes atendidas: %d%n", cantidadActivas(t.id), cantidadHistoricas(t.id));
    }
    private void listarProveedores() {
        proveedores.values().forEach(p -> System.out.printf("  #%d | %-24s | %s%n", p.id, p.nombre, p.activo ? "Activo" : "Inactivo"));
    }
    private void consultaRepuestosResumen() {
        for (Repuesto r : repuestos.values()) System.out.printf("  #%d %-25s | físico %3d | reservado %3d | DISP. %3d | mínimo %2d%n",
                r.id, r.nombre, stockFisico(r.id), stockReservado(r.id), stockDisponible(r.id), r.minimo);
    }
    private void consultaLotesResumen() {
        if (lotes.isEmpty()) System.out.println("  Sin lotes.");
        for (Lote l : lotes.values()) System.out.printf("  #%d %-12s | %s | %s | %d/%d unidades | costo %s%n",
                l.id, l.codigo, proveedores.get(l.proveedorId).nombre, repuestos.get(l.repuestoId).nombre,
                l.cantidadActual, l.cantidadOriginal, pesos(l.costoAdquisicion));
    }
    private void mostrarAlertas() {
        boolean alguna = false;
        for (Repuesto r : repuestos.values()) if (stockDisponible(r.id) <= r.minimo) {
            alguna = true;
            System.out.printf("  [ALERTA] %s | disponible %d, mínimo %d%n", r.nombre, stockDisponible(r.id), r.minimo);
        }
        if (!alguna) System.out.println("  No hay repuestos bajo el mínimo disponible.");
    }
    private void listarFacturas() {
        if (facturas.isEmpty()) System.out.println("  Sin facturas.");
        facturas.values().forEach(f -> System.out.printf("  Factura #%d | Orden #%d | %s | %s%n",
                f.id, f.ordenId, f.fecha, pesos(f.total)));
    }
    private void listarHistorialVehiculos() {
        for (Vehiculo v : vehiculos.values()) {
            long hechos = ordenes.values().stream().filter(o -> o.vehiculoId == v.id && !o.estado.esActiva()).count();
            System.out.printf("  Vehículo #%d | %s | %s | trabajos terminados: %d%n", v.id, v.placa, v.descripcion, hechos);
        }
        System.out.println("  [Prototipo] Recordatorios preventivos por periodicidad: aún no simulados en esta pantalla.");
    }

    private void reporteOrdenes() {
        for (Estado e : Estado.values()) System.out.printf("  %-12s : %d%n", e.etiqueta,
                ordenes.values().stream().filter(o -> o.estado == e).count());
        System.out.println("  Mantenimiento Preventivo: " + ordenes.values().stream().filter(o -> o.tipo.equals("Preventivo")).count());
        System.out.println("  Mantenimiento Correctivo: " + ordenes.values().stream().filter(o -> o.tipo.equals("Correctivo")).count());
    }
    private void reporteTecnicos() {
        for (Tecnico t : tecnicos.values()) {
            long cerradas = ordenes.values().stream().filter(o -> !o.estado.esActiva() && o.tecnicos.contains(t.id)).count();
            BigDecimal h = ordenes.values().stream().filter(o -> o.horas.containsKey(t.id))
                    .map(o -> o.horas.get(t.id)).reduce(BigDecimal.ZERO, BigDecimal::add);
            System.out.printf("  %-22s | trabajos finalizados: %-2d | horas registradas: %s%n", t.nombre, cerradas, h.toPlainString());
        }
        System.out.println("  Métrica de productividad de este prototipo: participaciones en órdenes cerradas, no eficiencia.");
    }
    private void reporteConsumos() {
        for (Repuesto r : repuestos.values()) {
            int total = ordenes.values().stream().mapToInt(o -> o.consumos.getOrDefault(r.id, 0)).sum();
            System.out.printf("  %-26s : %d unidades consumidas en órdenes DEMO%n", r.nombre, total);
        }
    }
    private void reporteProveedores() {
        for (Proveedor p : proveedores.values()) {
            List<Evaluacion> lista = evaluaciones.values().stream().filter(e -> e.proveedorId == p.id).toList();
            if (lista.isEmpty()) System.out.printf("  %s | Sin evaluaciones%n", p.nombre);
            else {
                double calidad = lista.stream().mapToInt(e -> e.calidad).average().orElseThrow();
                double puntualidad = lista.stream().mapToInt(e -> e.puntualidad).average().orElseThrow();
                System.out.printf(Locale.ROOT, "  %s | n=%d | calidad %.2f/5 | puntualidad %.2f/5%n",
                        p.nombre, lista.size(), calidad, puntualidad);
            }
        }
    }
    private void reporteFacturacion() {
        BigDecimal total = facturas.values().stream().map(f -> f.total).reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("  [REPORTE FUTURO] Total emitido en facturas simuladas: " + pesos(total));
        System.out.println("  Facturas: " + facturas.size() + " | No representa flujo de caja ni rentabilidad.");
    }
    private void reporteCompras() {
        BigDecimal total = lotes.values().stream()
                .map(l -> l.costoAdquisicion.multiply(BigDecimal.valueOf(l.cantidadOriginal)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("  [REPORTE FUTURO] Valor histórico de recepción de lotes: " + pesos(total));
        System.out.println("  Lotes recibidos: " + lotes.size() + " | No equivale a compras pagadas.");
    }

    private boolean tieneOrdenActiva(int tecnicoId) { return cantidadActivas(tecnicoId) > 0; }
    private int cantidadActivas(int tecnicoId) {
        return (int) ordenes.values().stream().filter(o -> o.estado.esActiva() && o.tecnicos.contains(tecnicoId)).count();
    }
    private int cantidadHistoricas(int tecnicoId) {
        return (int) ordenes.values().stream().filter(o -> !o.estado.esActiva() && o.tecnicos.contains(tecnicoId)).count();
    }
    private int stockFisico(int repuestoId) {
        return lotes.values().stream().filter(l -> l.repuestoId == repuestoId).mapToInt(l -> l.cantidadActual).sum();
    }
    private int stockReservado(int repuestoId) {
        return ordenes.values().stream().filter(o -> o.estado.esActiva())
                .mapToInt(o -> o.reservas.getOrDefault(repuestoId, 0)).sum();
    }
    private int stockDisponible(int repuestoId) { return stockFisico(repuestoId) - stockReservado(repuestoId); }
    private String placa(Orden o) { return vehiculos.get(o.vehiculoId).placa; }
    private Orden pedirOrden(String texto) {
        Integer id = pedirId(texto, ordenes);
        return id == null ? null : ordenes.get(id);
    }
    private <T> Integer pedirId(String pregunta, Map<Integer, T> mapa) {
        while (true) {
            Integer n = numeroPositivo(pregunta);
            if (n == null) return null;
            if (mapa.containsKey(n)) return n;
            aviso("Ese ID no existe. Comprueba la lista o escribe 0 para cancelar.");
        }
    }
    private Tecnico elegirTecnico(boolean soloActivos) {
        while (true) {
            cabecera("SELECCIÓN DE TÉCNICO", soloActivos ? "Solo técnicos activos" : "Todos los técnicos");
            List<Tecnico> lista = tecnicos.values().stream().filter(t -> !soloActivos || t.activo).toList();
            for (int i = 0; i < lista.size(); i++) {
                Tecnico t = lista.get(i);
                System.out.printf("  %d. %s | %s | %s%n", i + 1, t.nombre, t.especialidad, t.activo ? "Activo" : "Inactivo");
            }
            System.out.println("  0. Cancelar" + (rol == null ? "" : "    M. Panel principal"));
            String op = entrada("Selecciona número de la lista");
            if (op.equals("0")) return null;
            Integer numero = convertirNumero(op);
            if (numero != null && numero >= 1 && numero <= lista.size()) return lista.get(numero - 1);
            aviso("Número inválido. Intenta de nuevo.");
        }
    }
    private void cabecera(String titulo, String descripcion) {
        System.out.println("\n" + "=".repeat(68));
        System.out.println("  CARMOTORS | " + titulo);
        System.out.println("  " + descripcion);
        System.out.println("-".repeat(68));
    }
    private String entrada(String solicitud) {
        System.out.print("\n  " + solicitud + ": ");
        if (!teclado.hasNextLine()) throw new SalirPrograma();
        String linea = teclado.nextLine().trim();
        if (linea.equalsIgnoreCase("M") && rol != null) throw new IrInicio();
        return linea;
    }
    private String textoObligatorio(String pregunta) {
        while (true) {
            String s = entrada(pregunta);
            if (!s.isBlank()) return s;
            aviso("No dejes este dato vacío.");
        }
    }
    private Integer convertirNumero(String valor) {
        try { return Integer.parseInt(valor.trim()); }
        catch (NumberFormatException ex) { return null; }
    }
    private Integer numeroPositivo(String pregunta) {
        while (true) {
            String s = entrada(pregunta);
            Integer n = convertirNumero(s);
            if (n == null || n < 0) { aviso("Ingresa un número entero positivo o 0 para cancelar."); continue; }
            return n == 0 ? null : n;
        }
    }
    private Integer enteroNoNegativo(String pregunta, boolean ceroCancela) {
        while (true) {
            Integer n = convertirNumero(entrada(pregunta));
            if (n == null || n < 0) { aviso("Debes escribir un entero no negativo."); continue; }
            return ceroCancela && n == 0 ? null : n;
        }
    }
    private Integer rango(String pregunta, int min, int max) {
        while (true) {
            Integer n = convertirNumero(entrada(pregunta));
            if (n != null && n >= min && n <= max) return n;
            aviso("Debe estar entre " + min + " y " + max + ".");
        }
    }
    private BigDecimal decimalNoNegativo(String pregunta, boolean aceptarCero) {
        while (true) {
            String s = entrada(pregunta);
            try {
                BigDecimal valor = new BigDecimal(s).setScale(2, RoundingMode.UNNECESSARY);
                if (valor.signum() < 0 || (!aceptarCero && valor.signum() == 0)) {
                    aviso("Escribe un valor " + (aceptarCero ? "no negativo" : "positivo") + "."); continue;
                }
                if (valor.compareTo(new BigDecimal("99999999.99")) > 0) { aviso("Importe fuera de rango de la demo."); continue; }
                return valor;
            } catch (NumberFormatException | ArithmeticException e) {
                aviso("Número inválido. Usa punto decimal y hasta 2 decimales (ejemplo: 3.50).");
            }
        }
    }
    private boolean confirmar(String accion) {
        while (true) {
            System.out.println("\n  [CONFIRMACIÓN] " + accion);
            System.out.println("  [S] Sí, continuar     [N] No, cancelar");
            String s = entrada("Tu decisión").toUpperCase(Locale.ROOT);
            if (s.equals("S") || s.equals("SI") || s.equals("SÍ")) return true;
            if (s.equals("N") || s.equals("NO") || s.equals("0")) { aviso("Operación cancelada; no se modificó nada."); return false; }
            aviso("Responde S o N.");
        }
    }
    private void aviso(String mensaje) { System.out.println("  [!] " + mensaje); }
    private void exito(String mensaje) { System.out.println("  [OK] " + mensaje); }
    private void postOperacion() {
        while (true) {
            System.out.println("\n  [1] Seguir en este apartado   [M] Panel principal   [S] Salir del programa");
            String op = entrada("Continuar").toUpperCase(Locale.ROOT);
            if (op.equals("1") || op.equals("0")) return;
            if (op.equals("S")) throw new SalirPrograma();
            aviso("Elige 1, M o S.");
        }
    }
    private void postLectura() {
        while (true) {
            System.out.println("\n  [0] Volver a los resultados   [M] Panel principal");
            if (entrada("Continuar").equals("0")) return;
            aviso("Elige 0 o M.");
        }
    }
}
