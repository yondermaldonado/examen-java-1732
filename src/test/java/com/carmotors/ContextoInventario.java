package com.carmotors;

import com.carmotors.config.ConexionFactory;
import com.carmotors.controlador.*;
import com.carmotors.dao.factory.*;
import com.carmotors.servicio.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Prepara todos los objetos que necesita MenuPruebasInventario.
 *
 * Este archivo NO muestra opciones de consola y NO contiene reglas de negocio.
 * Su trabajo es conectar las clases existentes para que el menu pueda usarlas.
 *
 * Recorrido normal:
 * MenuPruebasInventario -> Controller -> Service -> DAO -> MySQL.
 */
final class ContextoInventario {

    // Proporciona una conexion nueva a MySQL cuando un Service la solicita.
    // Se reutiliza la ConexionFactory que ya existe en el proyecto.
    final Supplier<Connection> conexiones;

    // Permite consultar la fecha de hoy en los servicios que la necesitan.
    final Supplier<LocalDate> hoy;

    // Publica los cambios confirmados de inventario a los observadores.
    final PublicadorInventario publicador;

    // Consulta de proveedores que ya utiliza el proyecto de pruebas.
    final ProveedorConsulta proveedores;

    // Servicios: contienen las reglas del negocio.
    final RepuestoTipoService tipos;
    final RepuestoService repuestos;
    final LoteService lotes;
    final ExistenciasService existencias;
    final AlertasInventarioService alertas;

    // Controladores: son los que llama el menu para sus operaciones.
    final RepuestoTipoController tiposCtrl;
    final RepuestoController repuestosCtrl;
    final LoteController lotesCtrl;
    final AlertasInventarioController alertasCtrl;

    // Guarda los ID de repuestos que notifico Observer durante esta ejecucion.
    final List<Integer> eventos;

    ContextoInventario() {
        // 1. Configurar la forma de obtener una conexion.
        conexiones = () -> ConexionFactory.getInstancia().crearConexion();

        // 2. Configurar de donde viene la fecha actual.
        hoy = LocalDate::now;

        // 3. Crear el publicador y la consulta de proveedores.
        publicador = new PublicadorInventario();
        proveedores = new ProveedorConsultaMemoria();

        // 4. Crear los servicios y pasarles los DAO necesarios.
        // RepuestoTipoService -> RepuestoTipoDAOJdbc -> MySQL.
        tipos = new RepuestoTipoService(new RepuestoTipoDAOFactory().obtenerDAO(), conexiones);

        // RepuestoService -> RepuestoDAOJdbc -> MySQL.
        // Recibe el publicador para avisar despues de los cambios confirmados.
        repuestos = new RepuestoService(
                new RepuestoDAOFactory().obtenerDAO(), proveedores, conexiones, publicador);

        // LoteService -> LoteDAOJdbc -> MySQL.
        // Tambien recibe la fecha y el publicador.
        lotes = new LoteService(
                new LoteDAOFactory().obtenerDAO(), proveedores, conexiones, hoy, publicador);

        // ExistenciasService -> ExistenciasDAOJdbc -> MySQL.
        // Consulta el fisico, las reservas y el disponible; no consume unidades.
        existencias = new ExistenciasService(
                new ExistenciasDAOFactory().obtenerDAO(), conexiones, hoy);

        // AlertasInventarioService revisa el stock y la caducidad.
        // El numero 7 es el umbral: avisa sobre lotes que caducan en 7 dias.
        alertas = new AlertasInventarioService(
                new AlertasDAOFactory().obtenerDAO(), existencias, conexiones, hoy, 7);

        // 5. Crear los controladores con sus servicios correspondientes.
        tiposCtrl = new RepuestoTipoController(tipos);
        repuestosCtrl = new RepuestoController(repuestos);
        lotesCtrl = new LoteController(lotes);
        alertasCtrl = new AlertasInventarioController(alertas);

        // 6. Crear la lista que guardara los eventos recibidos.
        eventos = new ArrayList<>();

        // 7. Conectar Observer con sus observadores.
        // El primero genera alertas; el segundo guarda el ID notificado.
        publicador.suscribir(alertas);
        publicador.suscribir(eventos::add);
    }

    /** Devuelve el nombre de la base MySQL a la que estamos conectados. */
    String baseActual() {
        String sql = "SELECT DATABASE()";

        try (Connection conexion = conexiones.get();
             Statement sentencia = conexion.createStatement();
             ResultSet resultado = sentencia.executeQuery(sql)) {

            if (resultado.next()) {
                return resultado.getString(1);
            }
            return null;

        } catch (Exception error) {
            // Si no hay conexion, el menu mostrara que no pudo detectar la base.
            return null;
        }
    }

    /**
     * Proteccion de las opciones que modifican registros.
     * Solo permitimos escrituras manuales sobre carmotors_test.
     */
    boolean basePruebasValida() {
        String nombreBase = baseActual();
        return "carmotors_test".equalsIgnoreCase(nombreBase);
    }
}
