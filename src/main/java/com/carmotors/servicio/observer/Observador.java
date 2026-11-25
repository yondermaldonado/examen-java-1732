package com.carmotors.servicio.observer;

import com.carmotors.modelo.EventoServicioCompletado;

/** Observer: contrato común para futuras notificaciones por SMS, WhatsApp, etc. */
@FunctionalInterface
public interface Observador {
    void actualizar(EventoServicioCompletado evento);
}
