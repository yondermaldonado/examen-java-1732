package com.carmotors.integracion;

import com.carmotors.config.ConexionFactory;
import com.carmotors.excepcion.ConexionException;
import java.math.BigDecimal;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RecorridoIntegradoTest {
    @Test void altaClienteHastaEntregaConRollbackDeFactura() throws Exception {
        try(Connection con=ConexionFactory.getInstancia().crearConexion()) {assumeTrue(con.isValid(2),"MySQL no disponible.");}
        catch(ConexionException e){assumeTrue(false,"MySQL no disponible.");}
        RecorridoIntegrado.Resultado r=RecorridoIntegrado.ejecutar();
        assertEquals("Entregado",r.estadoFinal());
        assertEquals(10,r.stockInicial());assertEquals(6,r.stockFinal());
        assertEquals(0,new BigDecimal("14756.00").compareTo(r.totalFactura()));
        assertTrue(r.rollbackFacturaVerificado());assertEquals(9,r.pasos().size());
    }
}
