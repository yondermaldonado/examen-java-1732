# Tarea 03 — Factory Method para creación de DAO

`ConexionFactory` ya era Singleton para abrir conexiones, pero no implementaba
Factory Method para DAO. Esta tarea añade el patrón conservando las interfaces
existentes y el manejo de transacciones de los servicios.

## Participantes

- Creador abstracto: `dao.factory.DAOFactory<D>`.
- Operación del creador: `obtenerDAO()`, que llama a `crearDAO()`.
- Factory Method: `protected abstract D crearDAO()`.
- Creadores concretos: `ClienteDAOFactory`, `VehiculoDAOFactory`,
  `ClienteVehiculosDAOFactory`, `RepuestoDAOFactory`, `LoteDAOFactory`,
  `ExistenciasDAOFactory`, `AlertasDAOFactory` y `TecnicoDAOFactory`.
- Productos: interfaces DAO; productos concretos: sus implementaciones `DAOJdbc`.

Cada subclase sobrescribe el método de creación. No hay método estático ni
switch que decida qué DAO construir. El genérico D permite reutilizar el creador
sin imponer un CRUD genérico incompatible con las interfaces de los compañeros.
Los creadores no abren, retienen ni cierran conexiones.

## Uso real en un servicio existente

`RepuestoService` incorpora un constructor que recibe el creador, utiliza
`obtenerDAO()` y delega sus operaciones al producto. Los constructores anteriores
siguen disponibles para no romper la integración de los compañeros.

```java
PublicadorInventario publicador = new PublicadorInventario();
RepuestoService servicio = new RepuestoService(
    new RepuestoDAOFactory(), proveedores,
    () -> ConexionFactory.getInstancia().crearConexion(), publicador);
```

`proveedores` es la implementación de `ProveedorConsulta` del módulo proveedor.
El mismo publicador debe compartirse con sus observadores cuando se ensamble la
aplicación. Este ejemplo no añade Main, menús ni cambia configuración JDBC.

## Ejemplo de técnicos solicitado en la issue #22

Se incorpora `Tecnico`, `TecnicoDAO`, `TecnicoDAOJdbc` y `TecnicoDAOFactory`.
El DAO implementa crear, buscar, listar, actualizar y eliminar usando
PreparedStatement y las columnas reales de `database/schema.sql`. No se añade
una tabla, ni servicio/controlador de técnicos, que corresponden a la siguiente
tarea. Las validaciones de negocio deberán residir en ese servicio.

```java
TecnicoDAO dao = new TecnicoDAOFactory().obtenerDAO();
try (Connection con = ConexionFactory.getInstancia().crearConexion()) {
    con.setAutoCommit(false);
    try {
        // dao.crear(con, tecnico); otros DAO reciben esta misma con.
        con.commit();
    } catch (RuntimeException e) {
        con.rollback();
        throw e;
    }
}
```

El servicio controla commit/rollback; el DAO cierra solo sentencias y resultados.
Para otra entidad se puede añadir un creador que extienda DAOFactory<SuDAO> y
sobrescriba crearDAO(). No es necesario rehacer los DAO existentes.

## Verificación

`mvn verify` ejecuta las pruebas del proyecto, incluida una prueba de sustitución
real de DAO mediante herencia desde RepuestoService. La prueba
`TecnicoDAOJdbcIntegracionTest` comprueba CRUD y rollback en MySQL local usando
la conexión de ConexionFactory. Si no hay servidor, se omite explícitamente.
No ejecuta schema.sql ni borra la base. Requiere previamente la tabla tecnicos.
