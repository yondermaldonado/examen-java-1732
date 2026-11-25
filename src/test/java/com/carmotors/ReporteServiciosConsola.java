package com.carmotors;

import com.carmotors.modelo.FilaReporteDTO;
import com.carmotors.modelo.FiltroReporte;
import com.carmotors.modelo.OrdenReporte;
import com.carmotors.modelo.ReporteDTO;
import com.carmotors.servicio.ReporteStrategy;
import com.carmotors.servicio.ServiciosMasSolicitadosStrategy;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public final class ReporteServiciosConsola {
    private ReporteServiciosConsola() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Indica la fecha inicial y final (AAAA-MM-DD) y, opcionalmente, el orden.");
        }

        OrdenReporte orden = args.length == 3 ? OrdenReporte.valueOf(args[2]) : OrdenReporte.CANTIDAD_DESC;
        FiltroReporte filtro;
        try {
            filtro = new FiltroReporte(LocalDate.parse(args[0]), LocalDate.parse(args[1]), orden);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Introduce fechas válidas con el formato AAAA-MM-DD.", e);
        }

        ReporteStrategy estrategia = new ServiciosMasSolicitadosStrategy();
        ReporteDTO reporte = estrategia.generar(filtro);
        System.out.println(reporte.titulo());
        if (reporte.filas().isEmpty()) {
            System.out.println("No se encontraron solicitudes en el periodo.");
            return;
        }

        System.out.printf("%-40s %12s%n", "Tipo de mantenimiento", "Solicitudes");
        for (FilaReporteDTO fila : reporte.filas()) {
            System.out.printf("%-40s %12d%n", fila.nombre(), fila.cantidad());
        }
    }
}
