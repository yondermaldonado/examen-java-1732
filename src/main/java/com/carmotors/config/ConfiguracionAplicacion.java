package com.carmotors.config;

import java.math.BigDecimal;
import java.nio.file.Path;

/** Valores de configuración compartidos por la aplicación. */
public final class ConfiguracionAplicacion {
    private ConfiguracionAplicacion() {}

    // Tasa acordada para el ejercicio CarMotors; no se presume una regla fiscal general.
    public static final BigDecimal TASA_IMPUESTO = new BigDecimal("0.19");
    public static final Path CARPETA_FACTURAS = Path.of("facturas");
}
