package com.carmotors.servicio;

import com.carmotors.modelo.FilaReporteDTO;
import com.carmotors.modelo.FiltroReporte;
import com.carmotors.modelo.OrdenReporte;
import com.carmotors.modelo.TipoReporte;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiciosMasSolicitadosStrategyTest {
    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 11, 30);
    private Connection fixture;
    private ReporteStrategy estrategia;

    @BeforeEach
    void cargarFixture() throws Exception {
        JdbcDataSource fuente = new JdbcDataSource();
        fuente.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        fixture = fuente.getConnection();
        try (Reader sql = new InputStreamReader(
                getClass().getResourceAsStream("/reportes/servicios-solicitados.sql"), StandardCharsets.UTF_8)) {
            RunScript.execute(fixture, sql);
        }
        estrategia = new ServiciosMasSolicitadosStrategy(() -> {
            try {
                return fuente.getConnection();
            } catch (SQLException e) {
                throw new AssertionError("No se pudo abrir la base de prueba", e);
            }
        });
    }

    @AfterEach
    void cerrarFixture() throws SQLException {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void cuentaPorTipoConLimitesInclusivosYOrdenDeDemanda() {
        var reporte = estrategia.generar(new FiltroReporte(INICIO, FIN, OrdenReporte.CANTIDAD_DESC));
        assertEquals(TipoReporte.SERVICIOS_SOLICITADOS, reporte.tipo());
        assertEquals(List.of(new FilaReporteDTO("Preventivo", "Preventivo", 2),
                new FilaReporteDTO("Correctivo", "Correctivo", 1)), reporte.filas());

        var ascendente = estrategia.generar(new FiltroReporte(INICIO, FIN, OrdenReporte.CANTIDAD_ASC));
        assertEquals(List.of("Correctivo", "Preventivo"),
                ascendente.filas().stream().map(FilaReporteDTO::nombre).toList());
    }

    @Test
    void desempataPorNombreEnUnPeriodoDeUnDia() {
        var reporte = estrategia.generar(new FiltroReporte(INICIO, INICIO, OrdenReporte.CANTIDAD_DESC));
        assertEquals(List.of(new FilaReporteDTO("Correctivo", "Correctivo", 1),
                new FilaReporteDTO("Preventivo", "Preventivo", 1)), reporte.filas());
    }

    @Test
    void devuelveListaVaciaCuandoElPeriodoNoTieneSolicitudes() {
        var reporte = estrategia.generar(new FiltroReporte(INICIO.minusMonths(2),
                INICIO.minusMonths(1), OrdenReporte.NOMBRE_ASC));
        assertTrue(reporte.filas().isEmpty());
    }
}
