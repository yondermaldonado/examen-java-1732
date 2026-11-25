# Tarea 08 — Reserva y consumo transaccional por orden

Rama local desde developer; se reutiliza el modelo aprobado, la política
PoliticaFefo, Factory Method y el contrato ConsumoReservasOrden de la tarea 07.
No se cambian tablas ni credenciales; JDBC sigue apuntando a MySQL local.

## Componentes

- InventarioReservas: puerto de persistencia para poder probar con dobles.
- InventarioReservasJdbc: consultas parametrizadas y bloqueos InnoDB.
- InventarioReservasFactory: creador concreto del puerto JDBC.
- ReservasOrdenService: reserva, ajuste, liberación y consumo; implementa
  ConsumoReservasOrden.
- ReservasOrdenController: adapta datos de la futura consola, sin leer/mostrar.
- LineaRepuestoOrden y ReservaOrden: datos de consulta del esquema existente.

## Reglas

`reservar(orden, repuesto, cantidad)` crea una línea nueva y reservas por lote,
con cantidades positivas y precio actual de venta guardado como histórico.
Si ya existe línea, se usa `ajustar(orden, repuesto, cantidadTotal)`.

Ajustar indica un total, no una suma. Libera reservas anteriores, calcula FEFO y
reserva nuevamente; conserva precio histórico y filas LIBERADA. Si no alcanza
stock, rollback restaura tanto la cantidad anterior como sus reservas activas.
Solo se usan lotes vigentes, primero los que vencen antes; sin caducidad al final,
con fecha de ingreso/id como desempate. La suma de existencias usa long para
no desbordar al sumar cantidades de distintos lotes.

Reservar o liberar NO cambia cantidad_actual. Stock físico es la suma por lotes;
la disponibilidad se obtiene restando reservas ACTIVAS. CONSUMIDA/LIBERADA no
se restan otra vez de la disponibilidad.

Liberar conserva la línea y el historial (la FK impide borrar una línea con
reservas históricas). Queda sin cobertura y no se puede completar hasta volver
a cubrirla con ajustar. Esta tarea no implementa cancelación de órdenes ni
eliminación del historial de reservas.

Solo Pendiente/En Proceso permite reservar/ajustar/liberar. Solo En Proceso
permite consumir, dentro de una Connection con autoCommit=false.
Las reservas activas deben cubrir exactamente la cantidad requerida, no estar
vencidas y no tener consumo previo. Se descuenta cantidad_actual y se marca
CONSUMIDA una sola vez, guardando fecha_consumo.

## Transacciones y concurrencia

Reserva/ajuste/liberación abren y controlan su propia transacción. Bloquean orden,
repuesto y lotes. El bloqueo del repuesto serializa el cálculo del disponible
entre órdenes que compiten por la misma pieza.

Completar usa OrdenServicioService: bloquea la orden, invoca este consumo con
la misma Connection y confirma stock + reservas + estado juntos. El consumo no
abre otra conexión, no hace commit/rollback/cierre y no publica eventos. Ante
fallos el servicio de órdenes revierte la operación completa. Los repuestos se
bloquean por ID ascendente y los lotes también, para reducir ciclos de bloqueo.

Las reservas activas de los lotes se leen con FOR UPDATE, no mediante un SUM
sobre un snapshot antiguo. Esto es necesario bajo REPEATABLE READ incluso si
antes se consultó la orden. Las actualizaciones de stock son condicionales y las
reservas solo pasan de ACTIVA a CONSUMIDA; las fechas y FK siguen protegidas por
MySQL. No se hacen reintentos silenciosos de un commit incierto.

Los eventos se publican únicamente después del commit, compartiendo la misma
instancia de PublicadorInventario con los observadores de alertas.

## Armado sin implementar aún facturación

```java
var conexiones = (java.util.function.Supplier<java.sql.Connection>)
    () -> ConexionFactory.getInstancia().crearConexion();
var reloj = java.time.Clock.systemDefaultZone();
var publicador = new PublicadorInventario(); // compartir/suscribir alertas
var daoOrdenes = new OrdenServicioDAOFactory().obtenerDAO();
var reservas = new ReservasOrdenService(daoOrdenes,
    new InventarioReservasFactory().obtenerDAO(), conexiones, reloj, publicador);
var servicioOrdenes = new OrdenServicioService(new OrdenServicioDAOFactory(),
    vehiculos, conexiones, reloj, reservas, publicador);

reservas.reservar(ordenId, repuestoId, 3);
// Iniciar antes si la orden está Pendiente.
servicioOrdenes.completarOrden(ordenId);
```

`vehiculos` implementa el puerto VehiculoConsulta existente. El constructor de
seis parámetros permite consumo real pero mantiene bloqueada la facturación.
Al integrarla se usa el constructor con FacturacionOrden. No hay Main nuevo ni
cambio a la lógica del compañero de inventario.

## Verificación

- ReservasOrdenServiceTest: reglas y colaboración sin servidor, FEFO y tipos.
- ReservasOrdenIntegracionTest: MySQL real con fixtures propios; FEFO, precio
  histórico, ajuste/liberación, vencimiento, rollback entre varios repuestos/lotes,
  fallo después del consumo, dos órdenes reservando simultáneamente y dos
  finalizaciones simultáneas sin descuento doble.
- `mvn verify` ejecuta además las pruebas anteriores del proyecto.

Las pruebas JDBC necesitan schema.sql y data.sql preparados en MySQL LOCAL DE
PRUEBAS. No recrean la base y eliminan solo sus fixtures identificados por IDs.
Si no hay servidor, las pruebas de integración se omiten explícitamente.
En esta sesión se ejecutaron en un contenedor MySQL 8.4 temporal, aislado del
repositorio y de cualquier base de los compañeros. No se versiona infraestructura
ni configuración de contenedor. La adaptación del host del usuario dentro de ese
contenedor es exclusiva de pruebas, no modifica schema.sql.
