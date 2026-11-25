package com.carmotors.modelo;

import java.time.LocalDateTime;
import java.util.Objects;

/** Datos mínimos e inmutables del evento, sin acceso a MySQL ni a la consola. */
public record EventoServicioCompletado(
        int servicioId, int vehiculoId, String placa,
        int clienteId, String nombreCliente, LocalDateTime fechaCompletado) {
    public EventoServicioCompletado {
        if (servicioId <= 0 || vehiculoId <= 0 || clienteId <= 0) {
            throw new IllegalArgumentException("Los IDs del evento deben ser positivos.");
        }
        Objects.requireNonNull(fechaCompletado, "La fecha es obligatoria.");
        if (placa == null || placa.isBlank() || nombreCliente == null || nombreCliente.isBlank()) {
            throw new IllegalArgumentException("El cliente y la placa son obligatorios.");
        }
        placa = placa.strip();
        nombreCliente = nombreCliente.strip();
    }
}
