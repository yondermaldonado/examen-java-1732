package com.carmotors.servicio.observer;

import com.carmotors.modelo.EventoServicioCompletado;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;
import java.util.Objects;


public final class ObservadorNotificacionCliente implements Observador {
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path destino;

    public ObservadorNotificacionCliente() {
        this(Path.of("notificaciones_clientes.txt"));
    }

    public ObservadorNotificacionCliente(Path ruta) {
        this.destino = Objects.requireNonNull(ruta, "El archivo de destino es obligatorio.");
    }

    @Override
    public synchronized void actualizar(EventoServicioCompletado detalle) {
        Objects.requireNonNull(detalle, "No se puede notificar un evento vacío.");
        String texto = "[" + FECHA_HORA.format(detalle.fechaCompletado()) + "] - NOTIFICACIÓN PARA "
                + limpiar(detalle.nombreCliente()) + ": Su vehículo con placa "
                + limpiar(detalle.placa()) + " está listo para ser recogido.";
        try (BufferedWriter escritor = Files.newBufferedWriter(destino, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            escritor.write(texto);
            escritor.newLine();
        } catch (IOException causa) {
            throw new UncheckedIOException("Error al guardar el aviso de la orden #"
                    + detalle.servicioId(), causa);
        }
    }

    private String limpiar(String campo) {
        return campo.replace('\r', ' ').replace('\n', ' ');
    }
}
