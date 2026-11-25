package com.carmotors.servicio;

import com.carmotors.dao.ConsumoRepuestosDAOJdbc;
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

class ConsumoRepuestosStrategyTest {
    @Test
    void sumaUnidadesConsumidasPorFechaYOrdenaLosResultados() throws Exception {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        try (Connection fixture = fuente.getConnection();
             var sql = new InputStreamReader(getClass().getResourceAsStream(
                     "/reportes/consumo-repuestos.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(fixture, sql);
            ReporteStrategy estrategia = new ConsumoRepuestosStrategy(
                    new ConsumoRepuestosDAOJdbc(), () -> abrir(fuente));

            LocalDate inicio = LocalDate.of(2026, 11, 1);
            LocalDate fin = inicio.withDayOfMonth(30);
            var filtro = new FiltroReporte(inicio, fin, OrdenReporte.CANTIDAD_DESC);
            var reporte = estrategia.generar(filtro);

            var aceite = new FilaReporteDTO("3", "Aceite", 2);
            var bujia = new FilaReporteDTO("2", "Bujía", 2);
            var filtroDeAceite = new FilaReporteDTO("1", "Filtro", 7);
            assertEquals(TipoReporte.CONSUMO_REPUESTOS, reporte.tipo());
            assertEquals(filtro, reporte.filtro());
            assertEquals(List.of(filtroDeAceite, aceite, bujia), reporte.filas());
            assertEquals(List.of(aceite, bujia, filtroDeAceite),
                    estrategia.generar(new FiltroReporte(inicio, fin, OrdenReporte.CANTIDAD_ASC)).filas());

            LocalDate diciembre = fin.plusDays(1);
            assertEquals(List.of(new FilaReporteDTO("1", "Filtro", 3_000_000_050L)),
                    estrategia.generar(new FiltroReporte(diciembre, diciembre, OrdenReporte.NOMBRE_ASC)).filas());
            assertTrue(estrategia.generar(new FiltroReporte(inicio.plusDays(1), inicio.plusDays(1),
                    OrdenReporte.CANTIDAD_DESC)).filas().isEmpty());
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
