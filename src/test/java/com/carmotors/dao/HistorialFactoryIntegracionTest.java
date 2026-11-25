package com.carmotors.dao;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.factory.HistorialServiciosDAOFactory;
import com.carmotors.excepcion.ConexionException;
import com.carmotors.servicio.HistorialServiciosConsultaJdbc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HistorialFactoryIntegracionTest {
    @Test void consultaHistorialRealATravesDelProductoDeLaFabrica() throws Exception {
        try(var con=ConexionFactory.getInstancia().crearConexion()) {assumeTrue(con.isValid(2),"MySQL no disponible");}
        catch(ConexionException e){assumeTrue(false,"MySQL no disponible");}
        var consulta=new HistorialServiciosConsultaJdbc(new HistorialServiciosDAOFactory().obtenerDAO());
        var historial=consulta.listarPorVehiculo(1);
        assertFalse(historial.isEmpty(),"Cargar schema.sql y data.sql en la base de pruebas");
        assertTrue(historial.stream().allMatch(s->s.getVehiculoId()==1));
        assertTrue(historial.stream().anyMatch(s->!s.getOperaciones().isEmpty()));
    }
}
