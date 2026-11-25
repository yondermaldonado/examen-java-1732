package com.carmotors.servicio;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicadorInventarioTest {
    @Test void notificaYPermiteDesuscribirDuranteLaPublicacion() {
        List<Integer> recibidos=new ArrayList<>();
        ObservadorInventario observador=recibidos::add;
        observador.inventarioCambio(1);
        observador.inventarioCambio(2);
        PublicadorInventario publicador=new PublicadorInventario();
        ObservadorInventario[] temporal=new ObservadorInventario[1];
        temporal[0]=id->{recibidos.add(-id);publicador.desuscribir(temporal[0]);};
        publicador.suscribir(temporal[0]);publicador.suscribir(observador);
        publicador.publicar(3);publicador.publicar(4);
        assertEquals(List.of(1,2,-3,3,4),recibidos);
    }
}
