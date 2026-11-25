
package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.HistorialController;

import com.carmotors.dao.ClienteDAOJdbc;
import com.carmotors.dao.HistorialServiciosDAOJdbc;
import com.carmotors.dao.VehiculoDAOJdbc;

import com.carmotors.excepcion.ClienteNoEncontradoException;
import com.carmotors.excepcion.VehiculoNoEncontradoException;

import com.carmotors.modelo.OperacionHistorial;
import com.carmotors.modelo.RecordatorioPreventivo;
import com.carmotors.modelo.ServicioHistorial;
import com.carmotors.modelo.Vehiculo;

import com.carmotors.servicio.HistorialService;
import com.carmotors.servicio.HistorialServiciosConsulta;
import com.carmotors.servicio.HistorialServiciosConsultaJdbc;
import com.carmotors.servicio.RecordatorioPreventivoService;
import com.carmotors.servicio.VehiculoService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class HistorialTest {

    private static int verificaciones = 0;

    public static void main(String[] args) throws SQLException {

        // Verificar que usamos la base de pruebas.
        try (Connection conexion =
                     ConexionFactory.getInstancia().crearConexion()) {

            if (!"carmotors_test".equalsIgnoreCase(
                    conexion.getCatalog())) {

                throw new IllegalStateException(
                        "Estas pruebas requieren carmotors_test"
                );
            }
        }

        VehiculoService vehiculoService = new VehiculoService(
                new VehiculoDAOJdbc(),
                new ClienteDAOJdbc()
        );

        int clienteId = 2;

        List<Vehiculo> vehiculos =
                vehiculoService.listarPorCliente(clienteId);

        if (vehiculos.size() < 2) {
            throw new IllegalStateException(
                    "Se requiere data.sql: el cliente 2 "
                            + "debe tener al menos dos vehiculos"
            );
        }

        int primerVehiculoId = vehiculos.get(0).getId();
        int segundoVehiculoId = vehiculos.get(1).getId();

        // FASE 2
        probarHistorial(
                vehiculoService,
                clienteId,
                primerVehiculoId,
                segundoVehiculoId
        );

        // FASE 3
        probarRecordatorios(
                vehiculoService,
                clienteId,
                primerVehiculoId,
                segundoVehiculoId
        );

        // FASE 4
        probarJdbc(
                vehiculoService,
                clienteId
        );

        // FASE 5
        probarControlador(
                vehiculoService,
                clienteId,
                primerVehiculoId,
                segundoVehiculoId
        );

        probarCasosLimite(vehiculoService);

        System.out.println(
                "\n========================================"
        );

        System.out.println(
                "VERIFICACIONES SUPERADAS: " + verificaciones
        );

        System.out.println(
                "========================================"
        );
    }

    // ========================================
    // FASE 2: HISTORIAL EN MEMORIA
    // ========================================

    private static void probarHistorial(
            VehiculoService vehiculoService,
            int clienteId,
            int primerVehiculoId,
            int segundoVehiculoId) {

        System.out.println(
                "\n========== HISTORIAL DE SERVICIOS =========="
        );

        HistorialServiciosEnMemoria memoria =
                new HistorialServiciosEnMemoria();

        OperacionHistorial aceite = new OperacionHistorial(
                1,
                "Cambio de aceite y filtro",
                new BigDecimal("90000"),
                6
        );

        ServicioHistorial anterior = new ServicioHistorial(
                1001,
                primerVehiculoId,
                LocalDateTime.of(2026, 1, 10, 8, 0),
                LocalDateTime.of(2026, 1, 11, 16, 0),
                "Entregado",
                "Preventivo",
                "Mantenimiento programado",
                List.of(aceite)
        );

        ServicioHistorial reciente = new ServicioHistorial(
                1002,
                primerVehiculoId,
                LocalDateTime.of(2026, 8, 10, 8, 0),
                LocalDateTime.of(2026, 8, 11, 16, 0),
                "Entregado",
                "Preventivo",
                "Nuevo cambio de aceite",
                List.of(aceite)
        );

        memoria.agregar(anterior);
        memoria.agregar(reciente);

        HistorialService historial =
                new HistorialService(vehiculoService, memoria);

        List<ServicioHistorial> servicios =
                historial.listarPorVehiculo(primerVehiculoId);

        verificar(
                servicios.size() == 2,
                "Consulta dos servicios de un vehiculo"
        );

        verificar(
                servicios.get(0).getServicioId() == 1002,
                "El servicio mas reciente aparece primero"
        );

        Map<Integer, List<ServicioHistorial>> historialCliente =
                historial.listarPorCliente(clienteId);

        verificar(
                historialCliente.size()
                        == vehiculoService.listarPorCliente(clienteId).size(),
                "Incluye todos los vehiculos del cliente"
        );

        verificar(
                historialCliente.containsKey(segundoVehiculoId)
                        && historialCliente
                        .get(segundoVehiculoId).isEmpty(),
                "Incluye vehiculos sin servicios registrados"
        );

        verificar(
                historialCliente.get(primerVehiculoId).size() == 2,
                "Agrupa los servicios en el vehiculo correcto"
        );

        try {
            historialCliente.clear();

            throw new IllegalStateException(
                    "La consulta permitio modificar el historial"
            );

        } catch (UnsupportedOperationException e) {

            verificar(
                    true,
                    "El resultado no permite modificaciones externas"
            );
        }

        try {
            historial.listarPorVehiculo(0);

            throw new IllegalStateException(
                    "Se acepto un ID invalido"
            );

        } catch (IllegalArgumentException e) {

            verificar(true, "Rechaza IDs invalidos");
        }
    }

    // ========================================
    // FASE 3: RECORDATORIOS EN MEMORIA
    // ========================================

    private static void probarRecordatorios(
            VehiculoService vehiculoService,
            int clienteId,
            int primerVehiculoId,
            int segundoVehiculoId) {

        System.out.println(
                "\n========== RECORDATORIOS PREVENTIVOS =========="
        );

        HistorialServiciosEnMemoria memoria =
                new HistorialServiciosEnMemoria();

        OperacionHistorial aceite = new OperacionHistorial(
                1,
                "Cambio de aceite y filtro",
                new BigDecimal("90000"),
                6
        );

        OperacionHistorial frenos = new OperacionHistorial(
                3,
                "Cambio de pastillas de freno",
                new BigDecimal("120000"),
                3
        );

        OperacionHistorial diagnostico = new OperacionHistorial(
                7,
                "Diagnostico general",
                new BigDecimal("50000"),
                null
        );

        memoria.agregar(new ServicioHistorial(
                2001,
                primerVehiculoId,
                LocalDateTime.of(2026, 1, 10, 8, 0),
                LocalDateTime.of(2026, 1, 11, 16, 0),
                "Entregado",
                "Preventivo",
                "Primer cambio de aceite",
                List.of(aceite)
        ));

        memoria.agregar(new ServicioHistorial(
                2002,
                primerVehiculoId,
                LocalDateTime.of(2026, 8, 10, 8, 0),
                LocalDateTime.of(2026, 8, 11, 16, 0),
                "Entregado",
                "Preventivo",
                "Segundo cambio de aceite",
                List.of(aceite)
        ));

        memoria.agregar(new ServicioHistorial(
                2003,
                primerVehiculoId,
                LocalDateTime.of(2026, 2, 1, 8, 0),
                LocalDateTime.of(2026, 2, 2, 16, 0),
                "Entregado",
                "Correctivo",
                "Cambio de pastillas",
                List.of(frenos)
        ));

        memoria.agregar(new ServicioHistorial(
                2004,
                primerVehiculoId,
                LocalDateTime.of(2026, 9, 20, 8, 0),
                LocalDateTime.of(2026, 9, 21, 16, 0),
                "Entregado",
                "Correctivo",
                "Diagnostico",
                List.of(diagnostico)
        ));

        memoria.agregar(new ServicioHistorial(
                2005,
                primerVehiculoId,
                LocalDateTime.of(2026, 10, 10, 8, 0),
                null,
                "En reparacion",
                "Preventivo",
                "Servicio en curso",
                List.of(aceite)
        ));

        memoria.agregar(new ServicioHistorial(
                2006,
                primerVehiculoId,
                LocalDateTime.of(2028, 1, 1, 8, 0),
                LocalDateTime.of(2028, 1, 2, 16, 0),
                "Entregado",
                "Preventivo",
                "Servicio futuro de prueba",
                List.of(aceite)
        ));

        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-17T12:00:00Z"),
                ZoneOffset.UTC
        );

        HistorialService historial =
                new HistorialService(vehiculoService, memoria);

        RecordatorioPreventivoService recordatorios =
                new RecordatorioPreventivoService(historial, reloj);

        List<RecordatorioPreventivo> lista =
                recordatorios.listarPorVehiculo(primerVehiculoId);

        verificar(
                lista.size() == 2,
                "Genera dos recordatorios distintos"
        );

        RecordatorioPreventivo primero = lista.get(0);
        RecordatorioPreventivo segundo = lista.get(1);

        verificar(
                primero.getOperacionId() == 3,
                "Ordena primero el mantenimiento mas proximo"
        );

        verificar(
                primero.getFechaProxima()
                        .equals(LocalDate.of(2026, 5, 2)),
                "Calcula la fecha de mantenimiento de frenos"
        );

        verificar(
                RecordatorioPreventivo.VENCIDO.equals(
                        primero.getEstado()),
                "Clasifica correctamente un mantenimiento vencido"
        );

        verificar(
                segundo.getOperacionId() == 1,
                "Incluye el recordatorio de aceite"
        );

        verificar(
                segundo.getFechaUltimaEntrega()
                        .equals(LocalDate.of(2026, 8, 11)),
                "Utiliza el ultimo cambio de aceite valido"
        );

        verificar(
                segundo.getFechaProxima()
                        .equals(LocalDate.of(2027, 2, 11)),
                "Calcula seis meses desde la ultima entrega"
        );

        verificar(
                RecordatorioPreventivo.PENDIENTE.equals(
                        segundo.getEstado()),
                "Clasifica correctamente un mantenimiento pendiente"
        );

        verificar(
                lista.stream()
                        .filter(r -> r.getOperacionId() == 1)
                        .count() == 1,
                "Evita duplicar recordatorios de una operacion"
        );

        verificar(
                lista.stream()
                        .noneMatch(r -> r.getOperacionId() == 7),
                "Ignora operaciones sin periodicidad"
        );

        Map<Integer, List<RecordatorioPreventivo>> porCliente =
                recordatorios.listarPorCliente(clienteId);

        verificar(
                porCliente.size()
                        == vehiculoService.listarPorCliente(clienteId).size(),
                "Incluye todos los vehiculos del cliente"
        );

        verificar(
                porCliente.containsKey(segundoVehiculoId)
                        && porCliente.get(segundoVehiculoId).isEmpty(),
                "Incluye vehiculos sin recordatorios"
        );

        Clock diaExacto = Clock.fixed(
                Instant.parse("2027-02-11T12:00:00Z"),
                ZoneOffset.UTC
        );

        RecordatorioPreventivoService enFecha =
                new RecordatorioPreventivoService(
                        historial, diaExacto);

        RecordatorioPreventivo aceiteEnFecha =
                enFecha.listarPorVehiculo(primerVehiculoId)
                        .stream()
                        .filter(r -> r.getOperacionId() == 1)
                        .findFirst()
                        .orElseThrow();

        verificar(
                RecordatorioPreventivo.PENDIENTE.equals(
                        aceiteEnFecha.getEstado()),
                "El dia del vencimiento sigue pendiente"
        );

        Clock diaSiguiente = Clock.fixed(
                Instant.parse("2027-02-12T12:00:00Z"),
                ZoneOffset.UTC
        );

        RecordatorioPreventivoService despues =
                new RecordatorioPreventivoService(
                        historial, diaSiguiente);

        RecordatorioPreventivo aceiteVencido =
                despues.listarPorVehiculo(primerVehiculoId)
                        .stream()
                        .filter(r -> r.getOperacionId() == 1)
                        .findFirst()
                        .orElseThrow();

        verificar(
                RecordatorioPreventivo.VENCIDO.equals(
                        aceiteVencido.getEstado()),
                "El dia siguiente cambia a vencido"
        );

        try {
            new RecordatorioPreventivo(
                    primerVehiculoId,
                    1,
                    "Cambio de aceite",
                    LocalDate.of(2026, 8, 11),
                    0,
                    LocalDate.of(2026, 11, 17)
            );

            throw new IllegalStateException(
                    "Se acepto una periodicidad invalida"
            );

        } catch (IllegalArgumentException e) {

            verificar(
                    true,
                    "Rechaza periodicidades invalidas"
            );
        }

        try {
            porCliente.clear();

            throw new IllegalStateException(
                    "Se permitio modificar el reporte"
            );

        } catch (UnsupportedOperationException e) {

            verificar(
                    true,
                    "Protege el reporte contra modificaciones externas"
            );
        }
    }

    // ========================================
    // FASE 4: INTEGRACION JDBC
    // ========================================

    private static void probarJdbc(
            VehiculoService vehiculoService,
            int clienteId) {

        System.out.println(
                "\n========== INTEGRACION JDBC MYSQL =========="
        );

        HistorialServiciosConsulta consultaJdbc =
                new HistorialServiciosConsultaJdbc(
                        new HistorialServiciosDAOJdbc()
                );

        List<ServicioHistorial> vehiculo2 =
                consultaJdbc.listarPorVehiculo(2);

        verificar(
                vehiculo2.stream()
                        .anyMatch(s -> s.getServicioId() == 2),
                "JDBC recupera la orden real 2"
        );

        ServicioHistorial servicio2 = vehiculo2.stream()
                .filter(s -> s.getServicioId() == 2)
                .findFirst()
                .orElseThrow();

        verificar(
                servicio2.getOperaciones().size() == 2,
                "JDBC agrupa las dos operaciones de una orden"
        );

        verificar(
                servicio2.getFechaEntrega() != null
                        && servicio2.getFechaEntrega()
                        .toLocalDate()
                        .equals(LocalDate.of(2026, 10, 10)),
                "JDBC recupera la fecha de entrega real"
        );

        verificar(
                servicio2.getOperaciones().stream()
                        .anyMatch(o ->
                                o.getCatalogoId() == 3
                                        && Integer.valueOf(12)
                                        .equals(o.getIntervaloMeses())),
                "JDBC recupera periodicidad del catalogo"
        );

        List<ServicioHistorial> vehiculo10 =
                consultaJdbc.listarPorVehiculo(10);

        verificar(
                vehiculo10.stream()
                        .anyMatch(s -> s.getServicioId() == 10),
                "JDBC consulta servicios de otro vehiculo"
        );

        verificar(
                consultaJdbc.listarPorVehiculo(
                        Integer.MAX_VALUE).isEmpty(),
                "JDBC devuelve lista vacia si no hay servicios"
        );

        HistorialService historialReal =
                new HistorialService(
                        vehiculoService,
                        consultaJdbc
                );

        Map<Integer, List<ServicioHistorial>> historialCliente =
                historialReal.listarPorCliente(clienteId);

        verificar(
                historialCliente.containsKey(2)
                        && historialCliente.containsKey(10),
                "JDBC incluye los dos vehiculos del cliente"
        );

        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-17T12:00:00Z"),
                ZoneOffset.UTC
        );

        RecordatorioPreventivoService recordatoriosReales =
                new RecordatorioPreventivoService(
                        historialReal,
                        reloj
                );

        Map<Integer, List<RecordatorioPreventivo>> reporte =
                recordatoriosReales.listarPorCliente(clienteId);

        verificar(
                reporte.get(2).stream().anyMatch(r ->
                        r.getOperacionId() == 3
                                && r.getFechaProxima().equals(
                                LocalDate.of(2027, 10, 10))
                                && RecordatorioPreventivo.PENDIENTE
                                .equals(r.getEstado())),
                "Calcula recordatorio desde registros reales"
        );

        verificar(
                reporte.containsKey(10)
                        && reporte.get(10).isEmpty(),
                "No genera recordatorios para operaciones sin intervalo"
        );
    }

    // ========================================
    // FASE 5: CONTROLADOR MVC
    // ========================================

    private static void probarControlador(
            VehiculoService vehiculoService,
            int clienteId,
            int primerVehiculoId,
            int segundoVehiculoId) {

        System.out.println(
                "\n========== CONTROLADOR MVC =========="
        );

        HistorialServiciosEnMemoria memoria =
                new HistorialServiciosEnMemoria();

        OperacionHistorial aceite = new OperacionHistorial(
                1,
                "Cambio de aceite y filtro",
                new BigDecimal("90000"),
                6
        );

        OperacionHistorial frenos = new OperacionHistorial(
                3,
                "Cambio de pastillas de freno",
                new BigDecimal("120000"),
                3
        );

        memoria.agregar(new ServicioHistorial(
                3001,
                primerVehiculoId,
                LocalDateTime.of(2026, 1, 1, 8, 0),
                LocalDateTime.of(2026, 1, 2, 16, 0),
                "Entregado",
                "Correctivo",
                "Cambio de frenos",
                List.of(frenos)
        ));

        memoria.agregar(new ServicioHistorial(
                3002,
                primerVehiculoId,
                LocalDateTime.of(2026, 8, 10, 8, 0),
                LocalDateTime.of(2026, 8, 11, 16, 0),
                "Entregado",
                "Preventivo",
                "Cambio de aceite",
                List.of(aceite)
        ));

        HistorialService historial =
                new HistorialService(vehiculoService, memoria);

        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-17T12:00:00Z"),
                ZoneOffset.UTC
        );

        RecordatorioPreventivoService recordatorios =
                new RecordatorioPreventivoService(
                        historial,
                        reloj
                );

        HistorialController controlador =
                new HistorialController(
                        historial,
                        recordatorios
                );

        // PRUEBA 33
        List<ServicioHistorial> servicios =
                controlador.consultarHistorialVehiculo(
                        primerVehiculoId
                );

        verificar(
                servicios.size() == 2
                        && servicios.get(0).getServicioId() == 3002,
                "Controlador consulta historial por vehiculo"
        );

        // PRUEBA 34
        Map<Integer, List<ServicioHistorial>> historiales =
                controlador.consultarHistorialCliente(clienteId);

        verificar(
                historiales.containsKey(primerVehiculoId)
                        && historiales.containsKey(segundoVehiculoId)
                        && historiales.get(segundoVehiculoId).isEmpty(),
                "Controlador consulta historial de todo el cliente"
        );

        // PRUEBA 35
        List<RecordatorioPreventivo> lista =
                controlador.consultarRecordatoriosVehiculo(
                        primerVehiculoId
                );

        verificar(
                lista.size() == 2,
                "Controlador consulta recordatorios de un vehiculo"
        );

        // PRUEBA 36
        Map<Integer, List<RecordatorioPreventivo>> reporte =
                controlador.consultarRecordatoriosCliente(clienteId);

        verificar(
                reporte.containsKey(primerVehiculoId)
                        && reporte.containsKey(segundoVehiculoId)
                        && reporte.get(segundoVehiculoId).isEmpty(),
                "Controlador consulta recordatorios de todo el cliente"
        );

        // PRUEBA 37
        verificar(
                lista.stream().anyMatch(r ->
                        r.getOperacionId() == 1
                                && RecordatorioPreventivo.PENDIENTE
                                .equals(r.getEstado())),
                "Controlador conserva los estados calculados"
        );
    }

    // ========================================
    // FASE 5: CASOS LIMITE CON MYSQL
    // ========================================

    private static void probarCasosLimite(
            VehiculoService vehiculoService) {

        System.out.println(
                "\n========== CASOS LIMITE =========="
        );

        HistorialServiciosConsulta consultaJdbc =
                new HistorialServiciosConsultaJdbc(
                        new HistorialServiciosDAOJdbc()
                );

        HistorialService historial =
                new HistorialService(
                        vehiculoService,
                        consultaJdbc
                );

        Clock reloj = Clock.fixed(
                Instant.parse("2026-11-17T12:00:00Z"),
                ZoneOffset.UTC
        );

        RecordatorioPreventivoService recordatorios =
                new RecordatorioPreventivoService(
                        historial,
                        reloj
                );

        HistorialController controlador =
                new HistorialController(
                        historial,
                        recordatorios
                );

        // En data.sql, el servicio 7 no tiene operaciones.
        List<ServicioHistorial> servicios7 =
                consultaJdbc.listarPorVehiculo(7);

        // PRUEBA 38
        verificar(
                servicios7.stream()
                        .anyMatch(s -> s.getServicioId() == 7),
                "Conserva una orden sin operaciones"
        );

        ServicioHistorial servicio7 =
                servicios7.stream()
                        .filter(s -> s.getServicioId() == 7)
                        .findFirst()
                        .orElseThrow();

        // PRUEBA 39
        verificar(
                servicio7.getOperaciones().isEmpty(),
                "Una orden sin detalles contiene lista vacia"
        );

        // PRUEBA 40
        verificar(
                servicio7.getFechaEntrega() == null,
                "Conserva la fecha de entrega nula"
        );

        // PRUEBA 41
        verificar(
                recordatorios.listarPorVehiculo(7).isEmpty(),
                "Una orden sin operaciones no genera recordatorios"
        );

        // PRUEBA 42: precio cobrado, no precio base.
        ServicioHistorial servicio2 =
                consultaJdbc.listarPorVehiculo(2)
                        .stream()
                        .filter(s -> s.getServicioId() == 2)
                        .findFirst()
                        .orElseThrow();

        verificar(
                servicio2.getOperaciones().stream()
                        .anyMatch(o ->
                                o.getCatalogoId() == 3
                                        && o.getPrecioCobrado().compareTo(
                                        new BigDecimal("120000.00")
                                ) == 0),
                "JDBC recupera el precio cobrado real"
        );

        // PRUEBA 43
        try {

            consultaJdbc.listarPorVehiculo(0);

            throw new IllegalStateException(
                    "Se acepto un ID invalido en JDBC"
            );

        } catch (IllegalArgumentException e) {

            verificar(
                    true,
                    "JDBC rechaza IDs menores o iguales a cero"
            );
        }

        // PRUEBA 44
        try {

            historial.listarPorVehiculo(999999999);

            throw new IllegalStateException(
                    "Se acepto un vehiculo inexistente"
            );

        } catch (VehiculoNoEncontradoException e) {

            verificar(
                    true,
                    "Historial rechaza vehiculos inexistentes"
            );
        }

        // PRUEBA 45
        try {

            historial.listarPorCliente(999999999);

            throw new IllegalStateException(
                    "Se acepto un cliente inexistente"
            );

        } catch (ClienteNoEncontradoException e) {

            verificar(
                    true,
                    "Historial rechaza clientes inexistentes"
            );
        }

        // PRUEBA 46
        try {

            controlador.consultarRecordatoriosVehiculo(0);

            throw new IllegalStateException(
                    "El controlador acepto un ID invalido"
            );

        } catch (IllegalArgumentException e) {

            verificar(
                    true,
                    "Controlador rechaza IDs invalidos"
            );
        }
    }

    // ========================================
    // METODO AUXILIAR
    // ========================================

    private static void verificar(
            boolean condicion,
            String descripcion) {

        if (!condicion) {
            throw new IllegalStateException(
                    "[FALLO] " + descripcion
            );
        }

        verificaciones++;

        System.out.println("[OK] " + descripcion);
    }
}
