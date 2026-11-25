package com.carmotors.vista;

import com.carmotors.controlador.OrdenServicioController;
import com.carmotors.controlador.VehiculoController;
import com.carmotors.modelo.Vehiculo;

import java.util.Objects;
import java.util.Optional;


public final class
VistaOrdenes {
    private final Consola consola;
    private final OrdenServicioController ordenes;
    private final VehiculoController vehiculos;

    public VistaOrdenes(Consola consola, OrdenServicioController ordenes, VehiculoController vehiculos) {
        this.consola = Objects.requireNonNull(consola);
        this.ordenes = Objects.requireNonNull(ordenes);
        this.vehiculos = Objects.requireNonNull(vehiculos);
    }

    public void mostrarAdministracion() {
        navegarAdministrador();
    }

    public void mostrarParaTecnico() {
        while (true) {
            consola.titulo("ÓRDENES DE SERVICIO | TÉCNICO");
            consola.mostrar("1. Crear orden de servicio");
            consola.mostrar("0. Regresar");
            String opcion = consola.leerOpcion("Opción", "1", "0");
            if (opcion == null || "0".equals(opcion)) return;
            crear();
        }
    }

    private void navegarAdministrador() {
        while (true) {
            consola.titulo("ATENCIÓN Y ÓRDENES | ADMINISTRADOR");
            consola.mostrar("1. Crear orden de servicio");
            consola.mostrar("2. Buscar orden por número");
            consola.mostrar("3. Listar órdenes");
            consola.mostrar("4. Consultar órdenes por vehículo (placa)");
            consola.mostrar("5. Consultar órdenes por estado");
            consola.mostrar("6. Completar Orden de Servicio");
            consola.mostrar("0. Regresar");
            String opcion = consola.leerOpcion("Opción", "1", "2", "3", "4", "5", "6", "0");
            if (opcion == null || "0".equals(opcion)) return;
            switch (opcion) {
                case "1" -> crear();
                case "2" -> buscarPorId();
                case "3" -> ejecutar(() -> consola.mostrar(ordenes.listar()));
                case "4" -> listarPorVehiculo();
                case "5" -> listarPorEstado();
                case "6" -> completarOrden();
                default -> throw new IllegalStateException("Opción ya validada");
            }
        }
    }


    private void completarOrden() {
        consola.titulo("COMPLETAR ORDEN DE SERVICIO");
        consola.mostrar("Órdenes en proceso:");
        consola.mostrar(ordenes.listarPorEstado("En Proceso"));
        Integer id = enteroPositivo("Número de orden En Proceso");
        if (id == null) return;
        consola.mostrar(ordenes.buscarPorId(id.toString()));
        consola.mostrar("Se consumirán las reservas y después del commit se enviarán los avisos.");
        if (!consola.confirmar("¿Confirmar finalización de la orden #" + id + "?")) return;
        ejecutar(() -> consola.mostrar(ordenes.cambiarEstado(id.toString(), "Completado")));
    }


    private void crear() {
        consola.titulo("NUEVA ORDEN DE SERVICIO");
        String placa = obligatorio("Placa del vehículo registrado");
        if (placa == null) return;
        Integer vehiculoId = buscarIdVehiculo(placa);
        if (vehiculoId == null) return;

        consola.mostrar("1. Mantenimiento preventivo");
        consola.mostrar("2. Mantenimiento correctivo");
        consola.mostrar("0. Cancelar");
        String opcion = consola.leerOpcion("Tipo", "1", "2", "0");
        if (opcion == null || "0".equals(opcion)) return;
        String tipo = "1".equals(opcion) ? "Preventivo" : "Correctivo";
        String problema = obligatorio("Descripción del problema o trabajo solicitado");
        if (problema == null) return;

        consola.mostrar("Vehículo: " + placa + " | Mantenimiento: " + tipo);
        consola.mostrar("Problema: " + problema);
        if (!consola.confirmar("¿Registrar esta orden en MySQL?")) return;
        ejecutar(() -> {
            // No se solicita ni se asigna aquí el ID de la nueva orden.
            // OrdenServicioService + DAO obtienen la clave generada por MySQL.
            consola.mostrar(ordenes.crear(vehiculoId.toString(), tipo, problema));
        });
    }

    private void buscarPorId() {
        Integer id = enteroPositivo("Número de orden existente");
        if (id == null) return;
        ejecutar(() -> consola.mostrar(ordenes.buscarPorId(id.toString())));
    }

    private void listarPorVehiculo() {
        String placa = obligatorio("Placa del vehículo");
        if (placa == null) return;
        Integer id = buscarIdVehiculo(placa);
        if (id == null) return;
        ejecutar(() -> consola.mostrar(ordenes.listarPorVehiculo(id.toString())));
    }

    private void listarPorEstado() {
        consola.titulo("FILTRAR ÓRDENES POR ESTADO");
        consola.mostrar("1. Pendiente");
        consola.mostrar("2. En Proceso");
        consola.mostrar("3. Completado");
        consola.mostrar("4. Facturado");
        consola.mostrar("5. Entregado");
        consola.mostrar("0. Regresar");
        String opcion = consola.leerOpcion("Estado", "1", "2", "3", "4", "5", "0");
        if (opcion == null || "0".equals(opcion)) return;
        String[] estados = {"Pendiente", "En Proceso", "Completado", "Facturado", "Entregado"};
        String estado = estados[Integer.parseInt(opcion) - 1];
        ejecutar(() -> consola.mostrar(ordenes.listarPorEstado(estado)));
    }

    private Integer buscarIdVehiculo(String placa) {
        try {
            Optional<Vehiculo> encontrado = vehiculos.buscarVehiculoPorPlaca(placa);
            if (encontrado.isEmpty()) {
                consola.mostrar("[!] La placa no pertenece a un vehículo registrado.");
                consola.mostrar("Registra el vehículo antes de crear una orden para él.");
                consola.esperar();
                return null;
            }
            Vehiculo v = encontrado.get();
            consola.mostrar("Vehículo localizado: #" + v.getId() + " | Placa: " + v.getPlaca()
                    + " | Propietario #" + v.getClienteId());
            return v.getId();
        } catch (RuntimeException e) {
            consola.mostrar("[!] No se pudo consultar el vehículo: " + e.getMessage());
            consola.esperar();
            return null;
        }
    }

    private Integer enteroPositivo(String titulo) {
        while (true) {
            String dato = consola.leerTexto(titulo + " (0 = regresar)");
            if (dato == null || "0".equals(dato)) return null;
            try {
                int id = Integer.parseInt(dato);
                if (id > 0) return id;
            } catch (NumberFormatException ignored) { }
            consola.mostrar("[!] Se requiere un entero positivo.");
        }
    }
    private String obligatorio(String titulo) {
        while (true) {
            String dato = consola.leerTexto(titulo + " (0 = cancelar)");
            if (dato == null || "0".equals(dato)) return null;
            if (!dato.isBlank()) return dato;
            consola.mostrar("[!] Este dato es obligatorio.");
        }
    }

    private void ejecutar(Runnable accion) {
        try { accion.run(); }
        catch (RuntimeException e) {
            consola.mostrar("[!] No se completó la operación: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        consola.esperar();
    }
}
