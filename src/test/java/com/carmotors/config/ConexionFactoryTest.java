package com.carmotors.config;

import com.carmotors.excepcion.ConexionException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConexionFactoryTest {

    @Test
    void configuraSingletonAbreConexionesIndependientesYConservaErrores() throws Exception {
        String claveAnterior = System.getProperty("carmotors.db.password");
        System.setProperty("carmotors.db.password", "clave-prueba-unicamente");
        List<Driver> anteriores = java.util.Collections.list(DriverManager.getDrivers());
        for (Driver anterior : anteriores) DriverManager.deregisterDriver(anterior);
        DriverControlado driver = new DriverControlado();
        DriverManager.registerDriver(driver);
        try {
            ConexionFactory factory = ConexionFactory.getInstancia();
            assertSame(factory, ConexionFactory.getInstancia());
            try (Connection primera = factory.crearConexion(); Connection segunda = factory.crearConexion()) {
                assertNotSame(primera, segunda);
                assertFalse(primera.isClosed());
                assertFalse(segunda.isClosed());
                assertEquals("carmotors_admin", driver.credenciales.getProperty("user"));
                assertNotNull(driver.credenciales.getProperty("password"));
            }
            assertTrue(driver.cierres.stream().allMatch(AtomicBoolean::get));
            driver.fallar = true;
            ConexionException error = assertThrows(ConexionException.class, factory::crearConexion);
            assertInstanceOf(SQLException.class, error.getCause());
            assertEquals("08001", ((SQLException) error.getCause()).getSQLState());
            assertFalse(error.getMessage().contains(driver.credenciales.getProperty("password")));
        } finally {
            DriverManager.deregisterDriver(driver);
            // Restablecer drivers y Singleton para no contaminar otras pruebas.
            var campo = ConexionFactory.class.getDeclaredField("instancia");
            campo.setAccessible(true);
            campo.set(null, null);
            for (Driver anterior : anteriores) DriverManager.registerDriver(anterior);
            if (claveAnterior == null) System.clearProperty("carmotors.db.password");
            else System.setProperty("carmotors.db.password", claveAnterior);
        }
    }

    private static class DriverControlado implements Driver {
        final List<AtomicBoolean> cierres = new ArrayList<>();
        Properties credenciales;
        boolean fallar;

        public Connection connect(String url, Properties info) throws SQLException {
            if (!acceptsURL(url)) return null;
            if (fallar) throw new SQLException("Servidor no disponible", "08001");
            credenciales = info;
            AtomicBoolean cerrada = new AtomicBoolean(false);
            cierres.add(cerrada);
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
                        if (metodo.getName().equals("close")) { cerrada.set(true); return null; }
                        if (metodo.getName().equals("isClosed")) return cerrada.get();
                        throw new UnsupportedOperationException(metodo.getName());
                    });
        }
        public boolean acceptsURL(String url) { return url != null && url.startsWith("jdbc:mysql://localhost:3306/carmotors_test?"); }
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
        public int getMajorVersion() { return 1; }
        public int getMinorVersion() { return 0; }
        public boolean jdbcCompliant() { return false; }
        public Logger getParentLogger() { return Logger.getGlobal(); }
    }
}
