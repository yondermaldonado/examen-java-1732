
package com.carmotors.modelo;

public record FilaReporteDTO(
        String codigo,
        String nombre,
        long cantidad
) {
    public FilaReporteDTO {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Código vacío");
        }

        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("Nombre vacío");
        }

        if (cantidad < 0) {
            throw new IllegalArgumentException("Cantidad negativa");
        }
    }
}
