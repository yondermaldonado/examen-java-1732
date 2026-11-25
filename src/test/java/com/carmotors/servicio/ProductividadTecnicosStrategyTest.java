package com.carmotors.servicio;

import com.carmotors.dao.ParticipacionesTecnicosDAOJdbc;
import com.carmotors.modelo.FilaReporteDTO;
import com.carmotors.modelo.FiltroReporte;
import com.carmotors.modelo.OrdenReporte;
import com.carmotors.modelo.TipoReporte;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductividadTecnicosStrategyTest {
    @Test
    void cuentaParticipacionesPorTecnicoYUsaLaFechaDeCompletado() throws Exception {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (Connection fixture = fuente.getConnection();
             var sql = new InputStreamReader(getClass().getResourceAsStream(
                     "/reportes/productividad-tecnicos.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(fixture, sql);
            ReporteStrategy estrategia = new ProductividadTecnicosStrategy(
                    new ParticipacionesTecnicosDAOJdbc(), () -> abrir(fuente));

            LocalDate primero = LocalDate.of(2026, 11, 1);
            var reporte = estrategia.generar(new FiltroReporte(primero, primero.withDayOfMonth(30),
                    OrdenReporte.CANTIDAD_DESC));

            assertEquals(TipoReporte.PRODUCTIVIDAD_TECNICOS, reporte.tipo());
            assertEquals(List.of(new FilaReporteDTO("1", "Ana", 2),
                    new FilaReporteDTO("3", "Luis", 2), new FilaReporteDTO("2", "Ana", 1)), reporte.filas());
            assertEquals(5L, reporte.filas().stream().mapToLong(FilaReporteDTO::cantidad).sum());

            var inicio = estrategia.generar(new FiltroReporte(primero, primero, OrdenReporte.CANTIDAD_DESC));
            assertEquals(List.of(new FilaReporteDTO("1", "Ana", 1),
                    new FilaReporteDTO("2", "Ana", 1)), inicio.filas());

            var vacio = estrategia.generar(new FiltroReporte(primero.plusDays(1), primero.plusDays(1),
                    OrdenReporte.CANTIDAD_DESC));
            assertTrue(vacio.filas().isEmpty());
        }
    }

    private static Connection abrir(JdbcDataSource fuente) {
        try {
            return fuente.getConnection();
        } catch (SQLException e) {
            throw new AssertionError("No se pudo abrir la base de prueba", e);
        }
    }
}
