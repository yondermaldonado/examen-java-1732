package com.carmotors.servicio;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.carmotors.config.ConexionFactory;
import com.carmotors.dao.RepuestoDAOJdbc;
import com.carmotors.excepcion.ValidacionException;
import com.carmotors.modelo.Repuesto;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Necesita MySQL con schema.sql y data.sql cargados. Si no hay servidor, se omite. */
class RepuestoIntegracionTest {

    private static RepuestoService servicio;

    @BeforeAll
    static void prepararOOmitir() {
        boolean disponible;
        try (Connection con = ConexionFactory.getInstancia().crearConexion()) {
            disponible = con.isValid(2);
        } catch (Exception e) {
            disponible = false;
        }
        assumeTrue(disponible, "MySQL no disponible: se omiten las pruebas de integración");
        servicio = new RepuestoService(new RepuestoDAOJdbc(), new ProveedorConsultaMemoria(),
                () -> ConexionFactory.getInstancia().crearConexion());
    }

    @Test
    void listaElCatalogoDeData() {
        assertEquals(17, servicio.listar().size()); // 12 repuestos, 5 con dos proveedores
    }

    @Test
    void filtros() {
        assertEquals(4, servicio.buscarPorNombre("aceite").size());
        assertEquals(2, servicio.listarPorTipo(1).size());        // Lubricantes
        assertEquals(4, servicio.listarPorMarca(5).size());       // Brembo: 2 repuestos x 2 proveedores
        assertEquals(3, servicio.listarPorProveedor(2).size());
    }

    @Test
    void stockBajoSoloDiscosDeFreno() {
        List<Repuesto> bajos = servicio.listarStockBajo();
        assertFalse(bajos.isEmpty());
        assertTrue(bajos.stream().allMatch(r -> r.getId() == 6));
    }

    @Test
    void cicloCrearBuscarActualizarEliminar() {
        Repuesto r = new Repuesto();
        r.setNombre("Repuesto de prueba");
        r.setTipoId(1);
        r.setMarcaId(1);
        r.setProveedorId(1);
        r.setPrecioCompra(new BigDecimal("1000"));
        r.setPrecioVenta(new BigDecimal("2000.50"));
        r.setStockMinimo(3);
        int id = servicio.crear(r);
        try {
            List<Repuesto> creado = servicio.buscarPorId(id);
            assertEquals(1, creado.size());
            assertEquals(new BigDecimal("2000.50"), creado.get(0).getPrecioVenta());
            assertEquals("Repuestos del Oriente S.A.S.", creado.get(0).getProveedor());

            Repuesto cambio = creado.get(0);
            cambio.setNombre("Repuesto de prueba v2");
            cambio.setPrecioCompra(new BigDecimal("1500.00"));
            assertTrue(servicio.actualizar(cambio));
            Repuesto leido = servicio.buscarPorId(id).get(0);
            assertEquals("Repuesto de prueba v2", leido.getNombre());
            assertEquals(new BigDecimal("1500.00"), leido.getPrecioCompra());
        } finally {
            assertTrue(servicio.eliminar(id));
        }
        assertTrue(servicio.buscarPorId(id).isEmpty());
    }

    @Test
    void noEliminaUnRepuestoConLotesYConservaSusDatos() {
        assertThrows(ValidacionException.class, () -> servicio.eliminar(1));
        // El rollback debe haber dejado intacto el vínculo con su proveedor
        List<Repuesto> sigue = servicio.buscarPorId(1);
        assertEquals(1, sigue.size());
        assertEquals(2, sigue.get(0).getProveedorId());
    }

    @Test
    void rechazaTipoInexistenteSinGuardarNada() {
        Repuesto r = new Repuesto();
        r.setNombre("No debe quedar");
        r.setTipoId(99);
        r.setMarcaId(1);
        r.setProveedorId(1);
        r.setPrecioCompra(new BigDecimal("1000"));
        r.setPrecioVenta(new BigDecimal("2000"));
        assertThrows(ValidacionException.class, () -> servicio.crear(r));
        assertTrue(servicio.buscarPorNombre("No debe quedar").isEmpty());
    }
}