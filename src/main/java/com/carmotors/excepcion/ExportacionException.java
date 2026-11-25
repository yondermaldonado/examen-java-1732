package com.carmotors.excepcion;

/** Error al generar el archivo, independiente de la transacción de facturación. */
public class ExportacionException extends RuntimeException {
    public ExportacionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
