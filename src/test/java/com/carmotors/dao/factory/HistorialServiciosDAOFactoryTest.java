package com.carmotors.dao.factory;

import com.carmotors.dao.HistorialServiciosDAO;
import com.carmotors.dao.HistorialServiciosDAOJdbc;
import com.carmotors.excepcion.AccesoDatosException;
import com.carmotors.modelo.ServicioHistorial;
import com.carmotors.servicio.HistorialServiciosConsultaJdbc;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HistorialServiciosDAOFactoryTest {
    @Test void permiteSustituirElProductoSinAcoplarLaConsultaAJdbc() {
        List<ServicioHistorial> esperado=new ArrayList<>();
        AtomicBoolean cerrada=new AtomicBoolean();
        Connection con=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("close")){cerrada.set(true);return null;}
            throw new AssertionError(m.getName());
        });
        AtomicInteger creaciones=new AtomicInteger();
        DAOFactory<HistorialServiciosDAO> creador=new DAOFactory<>() {
            @Override protected HistorialServiciosDAO crearDAO() {
                creaciones.incrementAndGet();
                return (conexion,id)->{assertSame(con,conexion);assertEquals(7,id);return esperado;};
            }
        };
        HistorialServiciosConsultaJdbc consulta=new HistorialServiciosConsultaJdbc(creador.obtenerDAO(),()->con);
        assertSame(esperado,consulta.listarPorVehiculo(7));
        assertEquals(1,creaciones.get());assertTrue(cerrada.get());
    }

    @Test void productoJdbcSeConstruyeSinAbrirConexionYValidaAntesDeConsultar() {
        HistorialServiciosDAO producto=new HistorialServiciosDAOFactory().obtenerDAO();
        assertInstanceOf(HistorialServiciosDAOJdbc.class,producto);
        HistorialServiciosConsultaJdbc consulta=new HistorialServiciosConsultaJdbc(producto,()->{
            throw new AssertionError("No debería abrir conexión para un ID inválido");
        });
        assertThrows(IllegalArgumentException.class,()->consulta.listarPorVehiculo(0));
    }

    @Test void laConsultaCierraSuConexionAunqueFalleElProducto() {
        AtomicBoolean cerrada=new AtomicBoolean();
        Connection con=(Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("close")){cerrada.set(true);return null;}
            throw new AssertionError(m.getName());
        });
        AccesoDatosException fallo=new AccesoDatosException("Fallo JDBC",new SQLException("Error de prueba"));
        HistorialServiciosDAO producto=(conexion,id)->{throw fallo;};
        HistorialServiciosConsultaJdbc consulta=new HistorialServiciosConsultaJdbc(producto,()->con);
        assertSame(fallo,assertThrows(AccesoDatosException.class,()->consulta.listarPorVehiculo(1)));
        assertTrue(cerrada.get());
    }
}
