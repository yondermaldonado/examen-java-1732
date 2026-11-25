# Revisión de Factory Method después de integrar nuevos DAO

## Resultado de la revisión

DAOFactory<D> implementa correctamente el creador abstracto: obtenerDAO() llama
al método protegido crearDAO(), sobrescrito por cada creador concreto. No es una
fábrica estática con switch. Los creadores no abren ni almacenan conexiones.

ProveedorDAOFactory y RepuestoTipoDAOFactory ya estaban bien implementados y se
conservaron. Faltaba el creador del historial, cuyo adaptador dependía directamente
de HistorialServiciosDAOJdbc. Se incorporaron HistorialServiciosDAO (contrato de
lectura) y HistorialServiciosDAOFactory, y el adaptador ahora depende del contrato.
No se modificaron las consultas SQL ni las reglas de historial/recordatorios.

También se corrigió el armado de ContextoInventario y VistaPruebaCarMotors: ahora
solicitan los productos a sus fábricas en lugar de construir implementaciones
JDBC directamente. Los servicios mantienen sus constructores que reciben
interfaces DAO, útiles para inyectar productos y dobles sin forzar sobrecargas.

## Cobertura actual: 20 creadores concretos

| Producto JDBC | Creador |
|---|---|
| ClienteDAOJdbc | ClienteDAOFactory |
| ClienteVehiculosDAOJdbc | ClienteVehiculosDAOFactory |
| VehiculoDAOJdbc | VehiculoDAOFactory |
| HistorialServiciosDAOJdbc | HistorialServiciosDAOFactory |
| ProveedorDAOJdbc | ProveedorDAOFactory |
| RepuestoDAOJdbc | RepuestoDAOFactory |
| RepuestoTipoDAOJdbc | RepuestoTipoDAOFactory |
| LoteDAOJdbc | LoteDAOFactory |
| ExistenciasDAOJdbc | ExistenciasDAOFactory |
| AlertasDAOJdbc | AlertasDAOFactory |
| TecnicoDAOJdbc | TecnicoDAOFactory |
| OrdenServicioDAOJdbc | OrdenServicioDAOFactory |
| ServicioTecnicoDAOJdbc | ServicioTecnicoDAOFactory |
| InventarioReservasJdbc | InventarioReservasFactory |
| ProveedorRepuestoDAOJdbc | ProveedorRepuestoDAOFactory |
| EvaluacionProveedorDAOJdbc | EvaluacionProveedorDAOFactory |
| ReporteServiciosDAOJdbc | ReporteServiciosDAOFactory |
| ConsumoRepuestosDAOJdbc | ConsumoRepuestosDAOFactory |
| ParticipacionesTecnicosDAOJdbc | ParticipacionesTecnicosDAOFactory |
| FacturaDAOJdbc | FacturaDAOFactory |

InventarioReservas utiliza el puerto homónimo de servicio; HistorialServiciosDAO
es un DAO de lectura, no necesita un CRUD artificial. Los productos se devuelven
mediante sus interfaces, no mediante tipos de implementación.

## Ejemplo

```java
HistorialServiciosDAO dao = new HistorialServiciosDAOFactory().obtenerDAO();
HistorialServiciosConsulta consulta = new HistorialServiciosConsultaJdbc(dao);
```

La consulta abre/cierra Connection; el DAO recibe esa conexión. Se conserva el
constructor normal del adaptador y se añade uno con Supplier<Connection> para
pruebas independientes, sin cambiar la configuración local de ConexionFactory.

Las pruebas específicas de implementaciones JDBC y los dobles con fallos
inyectados pueden seguir creando clases concretas: su objetivo es precisamente
verificar esa implementación. Esto no implica creación directa en la aplicación.
No se sustituyen los dobles de prueba por fábricas JDBC que necesiten MySQL.

## Verificación

- HistorialServiciosDAOFactoryTest: sustitución del producto mediante herencia,
  validación previa al acceso y cierre de conexión ante error.
- HistorialFactoryIntegracionTest: fábrica → adaptador → DAO real → MySQL;
  consulta únicamente fixtures existentes, sin escribir datos.
- mvn verify: compilación y suite completa, incluida concurrencia de reservas.


## Reanudación sobre developer actualizado

Se mantuvieron los creadores de catálogo/evaluación de proveedores añadidos por
los compañeros. Se añadieron los cuatro que faltaban en reportes y facturación.
FacturaDAOJdbc y ReporteServiciosDAOJdbc ahora implementan interfaces de lectura/
escritura con las mismas firmas existentes; no cambió su SQL.

Los constructores normales de las tres estrategias de reportes y de
FacturacionService obtienen sus productos mediante Factory Method. Se conserva
la API anterior y se permite inyectar interfaces para verificar sustitución sin
MySQL. La Connection del llamador sigue siendo obligatoria en la facturación;
ninguna fábrica abre conexiones o confirma transacciones.

La aplicación no construye directamente productos JDBC fuera de dao/factory.
Los new de DAO concretos que permanecen en pruebas de integración o dobles con
fallos sirven para verificar esa implementación específica, no para ensamblar
la aplicación.

ServiciosFactoryMethodTest comprueba selección de producto para reportes y
facturas, resultados, uso de la misma conexión y rechazo de factura sin transacción.
