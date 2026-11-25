package com.carmotors.vista;

import com.carmotors.controlador.OrdenServicioController;

import java.util.Objects;

/**
 * Flujo administrativo de facturación y entrega de órdenes.
 *
 * Solo lee opciones y presenta resultados. OrdenServicioController delega
 * la transacción de facturación y entrega a OrdenServicioService.
 * Las facturas se crean en la base de datos, nunca en esta vista.
 */
public final class VistaFacturacion {
    private final Consola consola;
    private final OrdenServicioController ordenes;

    public VistaFacturacion(Consola consola, OrdenServicioController ordenes) {
        this.consola = Objects.requireNonNull(consola, "La consola es obligatoria");
        this.ordenes = Objects.requireNonNull(ordenes, "El controlador de órdenes es obligatorio");
    }

    public void mostrar(SesionConsola sesion) {
        Objects.requireNonNull(sesion, "La sesión es obligatoria");
        if (sesion.rol() != SesionConsola.Rol.ADMINISTRADOR) {
            throw new IllegalArgumentException("Se requiere rol administrativo");
        }
        while (true) {
            consola.titulo("FACTURACIÓN Y ENTREGA DE VEHÍCULOS");
            consola.mostrar("1. Consultar órdenes completadas (listas para facturar)");
            consola.mostrar("2. Facturar una orden completada");
            consola.mostrar("3. Consultar órdenes facturadas (pendientes de entrega)");
            consola.mostrar("4. Registrar entrega de vehículo facturado");
            consola.mostrar("5. Consultar una orden por número");
            consola.mostrar("6. Consultar órdenes entregadas");
            consola.mostrar("0. Regresar");
            String opcion = consola.leerOpcion("Opción", "1", "2", "3", "4", "5", "6", "0");
            if (opcion == null || "0".equals(opcion)) return;
            switch (opcion) {
                case "1" -> mostrarOrdenes("Completado");
                case "2" -> facturar();
                case "3" -> mostrarOrdenes("Facturado");
                case "4" -> entregar();
                case "5" -> consultarOrden();
                case "6" -> mostrarOrdenes("Entregado");
                default -> throw new IllegalStateException("Opción previamente validada");
            }
        }
    }

    private void mostrarOrdenes(String estado) {
        ejecutar(() -> {
            consola.titulo("ÓRDENES | " + estado.toUpperCase(java.util.Locale.ROOT));
            consola.mostrar(ordenes.listarPorEstado(estado));
        });
    }

    private void facturar() {
        consola.titulo("FACTURAR ORDEN COMPLETADA");
        if (!mostrarListado("Completado")) return;
        Integer id = leerIdOrden();
        if (id == null) return;
        ejecutar(() -> {
            consola.mostrar("Orden seleccionada:");
            consola.mostrar(ordenes.buscarPorId(String.valueOf(id)));
            consola.mostrar("La factura se generará con los importes registrados en MySQL.");
            consola.mostrar("Esta operación registra una factura y cambia el estado a Facturado.");
            if (!consola.confirmar("¿Confirmar facturación de la orden #" + id + "?")) {
                consola.mostrar("Facturación cancelada.");
                return;
            }
            // El Service verifica estado, duplicados y factura en la misma transacción.
            consola.mostrar(ordenes.cambiarEstado(String.valueOf(id), "Facturado"));
        });
    }

    private void entregar() {
        consola.titulo("REGISTRAR ENTREGA DE VEHÍCULO");
        if (!mostrarListado("Facturado")) return;
        Integer id = leerIdOrden();
        if (id == null) return;
        ejecutar(() -> {
            consola.mostrar("Orden seleccionada:");
            consola.mostrar(ordenes.buscarPorId(String.valueOf(id)));
            consola.mostrar("Esta operación deja la orden en estado Entregado.");
            if (!consola.confirmar("¿Confirmar la entrega de la orden #" + id + "?")) {
                consola.mostrar("Entrega cancelada.");
                return;
            }
            // La validación de factura existente y la transacción pertenecen al Service.
            consola.mostrar(ordenes.cambiarEstado(String.valueOf(id), "Entregado"));
        });
    }

    private boolean mostrarListado(String estado) {
        try {
            consola.mostrar("Órdenes con estado " + estado + ":");
            consola.mostrar(ordenes.listarPorEstado(estado));
            return true;
        } catch (RuntimeException e) {
            consola.mostrar("No se pudo consultar el listado: " + mensaje(e));
            consola.esperar();
            return false;
        }
    }

    private void consultarOrden() {
        Integer id = leerIdOrden();
        if (id != null) ejecutar(() -> consola.mostrar(ordenes.buscarPorId(String.valueOf(id))));
    }

    /** El usuario identifica una orden existente; no se genera un ID desde esta vista. */
    private Integer leerIdOrden() {
        while (true) {
            String entrada = consola.leerTexto("Número de orden existente (0 = cancelar)");
            if (entrada == null || "0".equals(entrada)) return null;
            try {
                int id = Integer.parseInt(entrada);
                if (id > 0) return id;
            } catch (NumberFormatException ignored) {
                // La vista informa el error y permite repetir la entrada.
            }
            consola.mostrar("Introduce un número de orden entero mayor que cero.");
        }
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (RuntimeException e) {
            consola.mostrar("No se pudo completar la operación: " + mensaje(e));
        }
        consola.esperar();
    }

    private String mensaje(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
