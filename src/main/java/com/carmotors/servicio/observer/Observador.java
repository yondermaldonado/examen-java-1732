package com.carmotors.servicio.observer;

import com.carmotors.modelo.EventoServicioCompletado;

@FunctionalInterface
public interface Observador {
    void actualizar(EventoServicioCompletado evento);
}
