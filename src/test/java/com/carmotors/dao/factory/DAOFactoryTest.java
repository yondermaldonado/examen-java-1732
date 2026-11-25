package com.carmotors.dao.factory;

import com.carmotors.dao.RepuestoDAO;
import com.carmotors.dao.RepuestoDAOJdbc;
import com.carmotors.dao.TecnicoDAOJdbc;
import com.carmotors.modelo.Repuesto;
import com.carmotors.servicio.PublicadorInventario;
import com.carmotors.servicio.RepuestoService;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DAOFactoryTest {
    @Test
    void elMetodoSobrescritoSeleccionaElDAOSinCambiarElServicio() {
        Repuesto repuesto = new Repuesto();
        repuesto.setNombre("Resultado de otro DAO");
        List<Repuesto> esperado = List.of(repuesto);
        AtomicBoolean cerrada = new AtomicBoolean();
        Connection conexion = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
                    if (metodo.getName().equals("close")) { cerrada.set(true); return null; }
                    throw new AssertionError("Operación JDBC inesperada: " + metodo.getName());
                });
        RepuestoDAO dao = (RepuestoDAO) Proxy.newProxyInstance(RepuestoDAO.class.getClassLoader(),
                new Class<?>[]{RepuestoDAO.class}, (proxy, metodo, args) -> {
                    assertEquals("listar", metodo.getName());
                    assertSame(conexion, args[0]);
                    return esperado;
                });
        AtomicInteger creaciones = new AtomicInteger();
        DAOFactory<RepuestoDAO> creador = new DAOFactory<>() {
            @Override protected RepuestoDAO crearDAO() {
                creaciones.incrementAndGet();
                return dao;
            }
        };
        RepuestoService servicio = new RepuestoService(creador, id -> true,
                () -> conexion, new PublicadorInventario());
        assertSame(esperado, servicio.listar());
        assertEquals(1, creaciones.get());
        assertTrue(cerrada.get(), "El servicio, no el creador, administra la conexión.");
    }

    @Test
    void creadoresJdbcConstruyenProductosSinConectarseAMysql() {
        assertInstanceOf(RepuestoDAOJdbc.class, new RepuestoDAOFactory().obtenerDAO());
        assertInstanceOf(TecnicoDAOJdbc.class, new TecnicoDAOFactory().obtenerDAO());
    }

    @Test
    void rechazaUnCreadorQueNoDevuelveProducto() {
        DAOFactory<Object> defectuoso = new DAOFactory<>() {
            @Override protected Object crearDAO() { return null; }
        };
        assertThrows(NullPointerException.class, defectuoso::obtenerDAO);
    }
}
