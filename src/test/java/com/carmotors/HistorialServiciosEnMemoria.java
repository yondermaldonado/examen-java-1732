
package com.carmotors;

import com.carmotors.modelo.ServicioHistorial;
import com.carmotors.servicio.HistorialServiciosConsulta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HistorialServiciosEnMemoria
        implements HistorialServiciosConsulta {

    // Almacena los servicios agrupados por ID de vehiculo.
    private final Map<Integer, List<ServicioHistorial>>
            serviciosPorVehiculo = new HashMap<>();

    // Agrega un servicio al historial de su vehiculo.
    public void agregar(ServicioHistorial servicio) {

        Objects.requireNonNull(
                servicio,
                "El servicio no puede ser null"
        );

        serviciosPorVehiculo
                .computeIfAbsent(
                        servicio.getVehiculoId(),
                        id -> new ArrayList<>()
                )
                .add(servicio);
    }

    // Implementacion del contrato de consulta.
    @Override
    public List<ServicioHistorial> listarPorVehiculo(int vehiculoId) {

        if (vehiculoId <= 0) {
            throw new IllegalArgumentException(
                    "El ID del vehiculo debe ser positivo"
            );
        }

        return List.copyOf(
                serviciosPorVehiculo.getOrDefault(
                        vehiculoId,
                        List.of()
                )
        );
    }
}
