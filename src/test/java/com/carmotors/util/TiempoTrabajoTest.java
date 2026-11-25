package com.carmotors.util;

import com.carmotors.excepcion.ValidacionException;
import com.carmotors.servicio.TiempoTrabajo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TiempoTrabajoTest {
    @Test void todasLasDuracionesConservanSusMinutosAlPersistir() {
        for (int minutos = 0; minutos <= TiempoTrabajo.MAXIMO_MINUTOS; minutos++) {
            assertEquals(minutos, TiempoTrabajo.minutos(TiempoTrabajo.horas(minutos)));
        }
    }
    @Test void formatoDeDuracionSinLimiteDeVeinticuatroHoras() {
        assertEquals(1515, TiempoTrabajo.leerMinutos("25:15"));
        assertEquals("02:30", TiempoTrabajo.formatear(TiempoTrabajo.horas(150)));
        assertEquals("999:59", TiempoTrabajo.formatear(TiempoTrabajo.horas(59999)));
        for (String invalido : new String[]{null, "", "2.5", "02:60", "-01:30", "1000:00"}) {
            assertThrows(ValidacionException.class, () -> TiempoTrabajo.leerMinutos(invalido));
        }
    }
}
