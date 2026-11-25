# Tarea 07 — Transiciones de estado

La rama parte del commit local de la tarea 06. El servicio de órdenes centraliza
el ciclo Pendiente → En Proceso → Completado → Facturado → Entregado, sin añadir
State ni otros patrones a los cinco acordados. No permite saltos, retrocesos o
repetir estados; repetir completar/facturar no vuelve a ejecutar los contratos.

## Transacción y fechas

OrdenServicioDAOJdbc bloquea la fila de servicios con SELECT FOR UPDATE, la misma
que bloquean las asignaciones. Valida el estado actual y actualiza de forma
condicional, con el mismo Connection que reciben consumo/facturación.
El servicio controla commit/rollback y comprueba fechas de recepción/completado.
Se guarda fecha_completado al completar y fecha_entrega al entregar; no se cambian
al avanzar por otros estados. No cambian esquema ni credenciales JDBC locales.

## Contratos de integración

- ConsumoReservasOrden.consumir(con, ordenId): consume stock y reservas una sola
  vez, sin commit/cierre ni eventos externos. Devuelve IDs de repuestos afectados.
  El servicio verifica después que toda cantidad requerida esté CONSUMIDA y no
  quede ninguna reserva ACTIVA. Una orden sin líneas tiene cero consumos pendientes.
  La implementación real de lotes/stock corresponde a la tarea 08.
- FacturacionOrden.facturar(con, ordenId, fecha): persiste una factura con sus
  importes sin cambiar el estado ni confirmar/cerrar conexión. El servicio rechaza
  facturas previas y verifica que exista una al terminar. La persistencia y el
  cambio a Facturado se confirman juntos; la implementación real es de facturación.

Estos contratos solo se implementan con dobles en las pruebas de esta tarea.
Los constructores antiguos siguen permitiendo alta, consulta e inicio. Si no se
inyectan los contratos reales, completar/facturar devuelve error y hace rollback;
no hay implementación de producción vacía que permita evadir stock/facturas.
Entregar exige que la factura esté persistida.

## Uso cuando estén disponibles las implementaciones

```java
OrdenServicioService servicio = new OrdenServicioService(
    new OrdenServicioDAOFactory(), vehiculos, conexiones, reloj,
    consumoReservas, facturacion, publicadorCompartido);
servicio.iniciarOrden(id);
servicio.completarOrden(id);
servicio.facturarOrden(id);
servicio.entregarOrden(id);
```

La misma secuencia es accesible mediante
OrdenServicioController.cambiarEstado(idTexto, estadoTexto).
No se implementa aquí la asignación/reserva de repuestos ni el cálculo de factura.

Los IDs afectados se notifican al PublicadorInventario solo después del commit;
con rollback no hay eventos. Los observadores fallidos no revierten el commit.
Como toda integración por contrato, la implementación real de consumo debe
actualizar correctamente las existencias; revisar solo las reservas no sustituye
las pruebas de stock/transacciones de la tarea 08.

## Verificación

Pruebas del ciclo completo, matriz de saltos/repeticiones, orden inexistente,
consumo incompleto, factura ausente/duplicada, fechas inválidas, rollback conjunto,
fallos de commit y publicación posterior al commit usando dobles de transacción.
La prueba JDBC existente verifica bloqueo, actualización condicional y rollback
si MySQL está disponible; de lo contrario se omite expresamente. No se afirma
haber ejecutado concurrencia real en este entorno.
