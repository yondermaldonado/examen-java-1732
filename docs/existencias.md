# Contrato de consultas de existencias — CarMotors

## Responsabilidad y dependencias

Depende del esquema e ingresos de lotes (#32). `ExistenciasDAO`/`ExistenciasDAOJdbc` solo consultan; `ExistenciasService` valida parámetros y calcula agregados. Las órdenes serán las únicas responsables de crear reservas, liberarlas y consumir unidades. No se implementan escrituras aquí.

## Unidades y contrato

Las cantidades son unidades enteras del mismo repuesto; no hay conversiones de presentación, cajas o litros. Todas las salidas usan `long` para evitar desbordar sumas SQL de varios lotes. Los precios no aparecen en estas consultas (el resto del proyecto usa `BigDecimal`).

- `porLote(loteId, repuestoId)` devuelve `Optional<StockLote>` vacío cuando la pareja no existe.
- `porRepuesto(repuestoId)` devuelve una lista de `StockLote` (vacía si no hay lotes).
- `resumen(repuestoId)` devuelve `StockRepuesto` con `fisico`, `reservadoActivo`, `disponible`, `disponibleUtilizable`.
- `StockLote`: `fisico`, `reservadoActivo`, `disponible()`, `vigente(hoy)`, `disponibleUtilizable(hoy)`.

**Físico** = SUM(cantidad_actual), sin descontar reservas; el stock físico puede incluir lotes vencidos.
**Reservado activo** = SUM(reservas_repuestos.cantidad) cuando `reserva_estados.nombre = 'ACTIVA'`. Una reserva no retira unidades físicas. CONSUMIDA y LIBERADA no se cuentan. El descuento físico al consumir compete a órdenes.
**Disponible contable** = físico - reservado activo. Por lote aplica la misma fórmula.
**Disponible utilizable** = suma de disponible contable de lotes vigentes para FEFO; un lote vence después del día indicado y es válido durante su fecha de caducidad, `NULL` significa sin caducidad.

La reserva activa debe mantenerse menor o igual al físico del lote: si los datos existentes incumplen la regla, `StockLote` rechaza el resultado, en vez de esconder inconsistencias con `GREATEST(0, ...)`.

### Fixture de ejemplo (sin MySQL)

`ExistenciasServiceTest` usa un `ExistenciasDAO` de memoria y la fecha fija 2026-11-16. El proveedor de conexiones devuelve `null` solamente en esta prueba porque el DAO simulado no utiliza JDBC; `try-with-resources` permite ese valor sin intentar cerrarlo. En producción se utiliza una conexión real:

| Lote | Caducidad | Físico | Reservado ACTIVA | Disponible | Utilizable |
|---|---|---:|---:|---:|---:|
| STOCK-101 | 2026-12-01 | 10 | 4 | 6 | 6 |
| STOCK-102 | NULL | 6 | 2 | 4 | 4 |
| STOCK-103 | 2026-11-15 | 5 | 0 | 5 | 0 |
| **Total** | | **21** | **6** | **15** | **10** |

Las 6 reservas activas son valores simulados de la fixture del DAO, no registros insertados en MySQL. Como el esquema exige que reservas reales apunten a servicios, las pruebas de integración que creen reservas deberán preparar sus propios clientes, vehículos, servicios, líneas y estados dentro de una transacción con rollback.

## Uso desde la aplicación

```java
ExistenciasService servicio = new ExistenciasService(
    new ExistenciasDAOFactory().obtenerDAO(),
    () -> ConexionFactory.getInstancia().crearConexion(),
    LocalDate::now
);
StockRepuesto resumen = servicio.resumen(7);
System.out.println(resumen.disponible());
System.out.println(resumen.disponibleUtilizable());
```

## Supuestos y riesgos de integración

1. Los nombres de estado coinciden con el esquema: `ACTIVA`, `CONSUMIDA`, `LIBERADA`.
2. Las órdenes mantienen atómicamente `cantidad_actual` y los estados de reserva al consumir, y evitan sobre-reservar.
3. Estas consultas muestran una fotografía puntual, **no bloquean filas** ni autorizan reservar. Antes de reservar, órdenes debe verificar stock con bloqueo/transacción para impedir carreras concurrentes.
4. La política FEFO ya existente excluye lotes vencidos; por eso se publica adicionalmente `disponibleUtilizable` sin cambiar la definición contable de `disponible`.
5. La lista `porRepuesto` refleja la base en el momento de la consulta; el agregado `resumen` suma una única consulta en una sola conexión.
6. No se modifican `schema.sql`, métodos de consumo ni datos reales.
