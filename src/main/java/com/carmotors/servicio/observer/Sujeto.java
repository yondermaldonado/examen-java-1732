package com.carmotors.servicio.observer;

import com.carmotors.modelo.EventoServicioCompletado;

public interface Sujeto {
    void registrarObservador(Observador observador);
    void removerObservador(Observador observador);
    void notificarObservadores(EventoServicioCompletado evento);
}
