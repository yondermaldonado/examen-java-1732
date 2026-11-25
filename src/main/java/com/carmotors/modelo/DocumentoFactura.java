package com.carmotors.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Datos persistidos para presentar una factura sin recalcular importes históricos. */
public record DocumentoFactura(long id, long ordenId, LocalDateTime fechaEmision,
        String cliente, String documento, String telefono, String email,
        String placa, String marca, String modelo, DatosOrdenParaFactura detalle,
        BigDecimal totalRepuestos, BigDecimal totalManoObra, BigDecimal impuestos, BigDecimal totalFinal) {}
