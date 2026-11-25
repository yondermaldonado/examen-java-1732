package com.carmotors.servicio.observer;

import com.carmotors.modelo.EventoServicioCompletado;
import java.io.PrintStream;
import java.util.Objects;

/** Salida interna intercambiable; no conoce DAO, archivo ni envío al cliente. */
public final class ObservadorLogServicio implements Observador {
    private final java.util.function.Supplier<PrintStream> salida;

    public ObservadorLogServicio() {
        this(() -> System.out);
    }

    public ObservadorLogServicio(PrintStream salida) {
        this(() -> salida);
    }

    private ObservadorLogServicio(java.util.function.Supplier<PrintStream> destino) {
        this.salida = Objects.requireNonNull(destino, "La salida es obligatoria.");
    }

    @Override
    public void actualizar(EventoServicioCompletado trabajo) {
        Objects.requireNonNull(trabajo, "El evento es obligatorio.");
        salida.get().printf("LOG: El servicio # %d para el vehículo %s ha sido completado.%n",
                trabajo.servicioId(), trabajo.placa());
    }
}
