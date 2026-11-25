# Módulo Lotes: ingresos, trazabilidad y FEFO

Registra lo que entra al taller (lotes de repuestos), permite saber de qué lote y de qué
proveedor viene cada unidad, calcula el stock y propone de qué lote tomar con la política
FEFO. Sigue MVC: el controlador convierte texto, el servicio valida y controla la
transacción, y el DAO es el único lugar con SQL. Java 17, JDBC con `PreparedStatement` y
`BigDecimal` para el dinero.

```
LoteController  ->  LoteService  ->  LoteDAO (LoteDAOJdbc)  ->  MySQL
 (texto y mensajes)   |  (reglas, commit)     (todo el SQL)
                      +-> PoliticaFefo (lógica pura, sin base de datos)
                      +-> ProveedorConsulta (interfaz; doble en memoria del módulo Repuesto)
```

| Capa | Clase | Paquete |
|---|---|---|
| Modelo | `Lote`, `LoteLinea` (entrada); `ExistenciaLote`, `AsignacionLote` (salida, son `record`) | `com.carmotors.modelo` |
| DAO | `LoteDAO`, `LoteDAOJdbc` | `com.carmotors.dao` |
| Servicio | `LoteService`, `PoliticaFefo` | `com.carmotors.servicio` |
| Controlador | `LoteController` | `com.carmotors.controlador` |

## 1. Modelo de datos y regla contra la divergencia de stock

Un ingreso se guarda en dos tablas:

- `lotes`: `id`, `codigo` (único, máximo 50), `proveedor_id`, `fecha_ingreso`.
- `lotes_repuestos`: una fila por repuesto del lote, con `cantidad_ingresada` (lo que llegó),
  `cantidad_actual` (lo que queda), `costo_unitario` y `fecha_caducidad` (puede ser `NULL`).

**No existe ninguna columna de stock agregado.** El stock de un repuesto se calcula siempre
desde los lotes:

| Dato | Cómo se calcula |
|---|---|
| Stock físico | `SUM(lotes_repuestos.cantidad_actual)` del repuesto (incluye lotes vencidos) |
| Reservado | `SUM(reservas_repuestos.cantidad)` con estado `ACTIVA` |
| Stock disponible | físico - reservado |

Como el stock tiene una sola fuente de verdad, no puede divergir de los lotes. Quien
descuente existencias (módulo de reservas) debe **disminuir `cantidad_actual` del lote en la
misma transacción** que marca la reserva como `CONSUMIDA`; la base impide dejarla negativa o
mayor que la ingresada (`CHECK`).

### Clases

`Lote` (entrada de `registrarIngreso`):

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `int` | Lo genera MySQL. |
| `codigo` | `String` | Identificador único del lote. |
| `proveedorId` | `int` | Debe existir. |
| `fechaIngreso` | `LocalDate` | No puede ser futura. |
| `lineas` | `List<LoteLinea>` | Al menos una. |

`LoteLinea` (un repuesto dentro del lote):

| Campo | Tipo | Notas |
|---|---|---|
| `repuestoId` | `int` | Debe existir; no puede repetirse en el mismo lote. |
| `cantidadIngresada` | `int` | Mayor que 0. Al ingresar, `cantidad_actual` queda igual a este valor. |
| `costoUnitario` | `BigDecimal` | No negativo, máximo 2 decimales, máximo `99999999.99`. |
| `fechaCaducidad` | `LocalDate` | Opcional (`null` = no caduca). |

`ExistenciaLote` (salida de las consultas; una fila por lote y repuesto): `loteId`, `codigo`,
`proveedorId`, `proveedor`, `fechaIngreso`, `repuestoId`, `repuesto`, `cantidadIngresada`,
`cantidadActual`, `cantidadDisponible` (actual menos reservas `ACTIVA` de ese lote),
`costoUnitario`, `fechaCaducidad`.

`AsignacionLote` (salida de FEFO): `loteId`, `codigo`, `repuestoId`, `cantidad`, `fechaCaducidad`.

## 2. Ingreso transaccional

`LoteService.registrarIngreso(Lote)` devuelve el id del lote. Orden:

1. **Valida** (sin abrir conexión).
2. Abre la conexión, `setAutoCommit(false)`.
3. El DAO inserta el lote y todas sus líneas.
4. `commit()`; si cualquier paso falla, `rollback()` y no queda **nada** guardado (ni el lote
   ni ninguna línea).

Validaciones (todas lanzan `ValidacionException`):

| Campo | Regla |
|---|---|
| Lote | No nulo. |
| `codigo` | Obligatorio, máximo 50 caracteres, único (si ya existe: "Ya existe un lote con ese código."). Se guarda sin espacios sobrantes. |
| `proveedorId` | Mayor que 0 y debe existir según `ProveedorConsulta`. |
| `fechaIngreso` | Obligatoria y no posterior a hoy. |
| `lineas` | Al menos una; sin repuestos repetidos. |
| `repuestoId` | Mayor que 0. Que exista lo comprueba la base (error 1452). |
| `cantidadIngresada` | Mayor que 0. |
| `costoUnitario` | Obligatorio, no negativo, máximo 2 decimales, tope `99999999.99`. Se compara con `compareTo`. |
| `fechaCaducidad` | Opcional. Si se indica, no puede ser anterior a `fechaIngreso`. Se admite la misma fecha. |

"Hoy" no se toma del reloj dentro del servicio: se inyecta con `Supplier<LocalDate>`
(producción: `LocalDate::now`; pruebas: una fecha fija).

## 3. Consulta de procedencia (trazabilidad)

| Método del servicio | Responde a |
|---|---|
| `buscarPorCodigo(codigo)` | ¿Qué trae este lote y quién lo entregó? Lista vacía si no existe. |
| `listarPorRepuesto(repuestoId)` | ¿En qué lotes (y de qué proveedor) hay este repuesto? |
| `listarPorProveedor(proveedorId)` | ¿Qué ha entregado este proveedor? |

Las tres usan esta consulta base, con un `WHERE` distinto (`l.codigo = ?`, `lr.repuesto_id = ?`
o `l.proveedor_id = ?`) y orden `l.fecha_ingreso, l.id, lr.repuesto_id`:

```sql
SELECT l.id AS lote_id, l.codigo AS codigo, l.proveedor_id AS proveedor_id, p.nombre AS proveedor,
       l.fecha_ingreso AS fecha_ingreso, lr.repuesto_id AS repuesto_id, r.nombre AS repuesto,
       lr.cantidad_ingresada AS cantidad_ingresada, lr.cantidad_actual AS cantidad_actual,
       lr.cantidad_actual - COALESCE((SELECT SUM(rr.cantidad) FROM reservas_repuestos AS rr
                                      INNER JOIN reserva_estados AS re ON re.id = rr.estado_id
                                      WHERE rr.lote_id = lr.lote_id AND rr.repuesto_id = lr.repuesto_id
                                        AND re.nombre = 'ACTIVA'), 0) AS cantidad_disponible,
       lr.costo_unitario AS costo_unitario, lr.fecha_caducidad AS fecha_caducidad
FROM lotes_repuestos AS lr
INNER JOIN lotes AS l ON l.id = lr.lote_id
INNER JOIN proveedores AS p ON p.id = l.proveedor_id
INNER JOIN repuestos AS r ON r.id = lr.repuesto_id
```

Stock: `SELECT COALESCE(SUM(lr.cantidad_actual), 0) AS total FROM lotes_repuestos AS lr WHERE lr.repuesto_id = ?`
y `SELECT COALESCE(SUM(rr.cantidad), 0) AS total FROM reservas_repuestos AS rr INNER JOIN reserva_estados AS re ON re.id = rr.estado_id WHERE rr.repuesto_id = ? AND re.nombre = 'ACTIVA'`.
El DAO solo **lee** `reservas_repuestos` (tabla del módulo de reservas).

### Trazabilidad hacia atrás (consultas de referencia, a partir de las reservas)

Las reservas ya guardan el lote de donde sale cada unidad; con ellas se responde el camino
inverso. Estas consultas están probadas contra `data.sql`; no tienen método Java:

```sql
-- Dado un servicio: ¿de qué lote y proveedor salió cada repuesto?
SELECT rr.servicio_id AS servicio_id, r.nombre AS repuesto, l.codigo AS lote,
       p.nombre AS proveedor, rr.cantidad AS cantidad, re.nombre AS estado
FROM reservas_repuestos AS rr
INNER JOIN repuestos AS r ON r.id = rr.repuesto_id
INNER JOIN lotes AS l ON l.id = rr.lote_id
INNER JOIN proveedores AS p ON p.id = l.proveedor_id
INNER JOIN reserva_estados AS re ON re.id = rr.estado_id
WHERE rr.servicio_id = ?
ORDER BY rr.id;

-- Dado un lote: ¿en qué servicios se usó?
SELECT rr.servicio_id AS servicio_id, r.nombre AS repuesto, rr.cantidad AS cantidad, re.nombre AS estado
FROM reservas_repuestos AS rr
INNER JOIN lotes AS l ON l.id = rr.lote_id
INNER JOIN repuestos AS r ON r.id = rr.repuesto_id
INNER JOIN reserva_estados AS re ON re.id = rr.estado_id
WHERE l.codigo = ?
ORDER BY rr.id;
```

Ejemplo con `data.sql`: el servicio 8 tomó 3 unidades de aceite del lote `L-2026-001` y 2 del
lote `L-2026-005`, ambos del proveedor Lubricantes Andinos.

## 4. Política FEFO (First Expired, First Out)

Sale primero lo que caduca primero. `LoteService.seleccionarFefo(repuestoId, cantidad)`
devuelve una **propuesta** (`List<AsignacionLote>`); **no reserva ni descuenta nada**.

Reglas (implementadas en `PoliticaFefo`):

1. Solo se usan lotes **vigentes**: sin caducidad, o con `fechaCaducidad` igual o posterior a
   hoy. Un lote que vence hoy todavía se puede usar; uno que venció ayer, no.
2. Solo se usan lotes con **unidades disponibles** (`cantidadDisponible` mayor que 0, es decir,
   sin contar lo ya reservado).
3. Orden: primero los lotes que **sí caducan**, del que vence antes al que vence después.
4. Los lotes **sin caducidad van al final**.
5. Desempate (misma caducidad, o ambos sin caducidad): sale primero el de **ingreso más
   antiguo** (FIFO); si aún empatan, el de menor id.
6. Si un lote no alcanza, se **completa con el siguiente**: la cantidad se reparte.
7. Si entre todos los lotes vigentes no alcanza, lanza `ValidacionException` ("Stock
   insuficiente: se piden X unidades y solo hay Y disponibles en lotes vigentes.").
8. La cantidad pedida debe ser mayor que 0.

Ejemplos con `data.sql` (hoy = 2026-11-13):

| Pedido | Resultado |
|---|---|
| Repuesto 1 (aceite 5W-30), 5 unidades | `L-2026-001`: 1 (caduca 2028-08-20, solo 1 disponible) y `L-2026-005`: 4 (caduca 2029-10-20) |
| Repuesto 11 (líquido de frenos), 5 unidades | `L-2026-003`: 5. El lote `L-2025-014` venció el 2026-10-31 y se excluye. |
| Repuesto 11, 11 unidades | Error: solo hay 10 vigentes. |
| Repuesto 8 (amortiguador), 1 unidad | Error: hay 2 físicas pero las 2 están reservadas. |

**Cómo debe usarlo el módulo de reservas:** pedir el plan con `seleccionarFefo`, crear una
reserva por cada `AsignacionLote` (lote y cantidad) y, al consumirla, disminuir
`cantidad_actual` del lote, todo dentro de la misma transacción.

## 5. Controlador (`LoteController`)

Recibe `String`, convierte y devuelve un mensaje listo para mostrar; nunca lanza excepciones.
Fechas en formato `AAAA-MM-DD`; una caducidad vacía significa "sin caducidad".

| Método | Resultado |
|---|---|
| `registrarIngreso(codigo, proveedorId, fechaIngreso, repuestoId, cantidad, costoUnitario, fechaCaducidad)` | `Lote registrado con id N.` (registra un lote con un repuesto) |
| `buscarPorCodigo(codigo)`, `listarPorRepuesto(id)`, `listarPorProveedor(id)` | Una línea por fila: `codigo \| proveedor \| ingreso \| repuesto \| ingresadas \| actuales \| disponibles \| caducidad`, o `No se encontraron lotes.` |
| `consultarStock(repuestoId)` | `Stock físico: X \| Stock disponible: Y` |
| `seleccionarFefo(repuestoId, cantidad)` | Plan FEFO, una línea por lote |

| Situación | Mensaje |
|---|---|
| Texto que no es número | `Error: escribe números válidos (...)` |
| Fecha mal escrita (por ejemplo `13/11/2026`) | `Error: escribe las fechas como AAAA-MM-DD, por ejemplo 2026-11-13.` |
| `ValidacionException` | `Error: <mensaje>` |
| `DaoException` o `ConexionException` | `Error del sistema: <mensaje>` |

El servicio acepta lotes con varias líneas; el controlador registra uno con un solo repuesto.

## 6. Errores de MySQL traducidos por el DAO

| Código | Cuándo | Mensaje |
|---|---|---|
| 1062 | Código de lote repetido | `ValidacionException`: "Ya existe un lote con ese código." |
| 1062 | Repuesto repetido en el lote | `ValidacionException`: "Registro duplicado: el repuesto ya está en ese lote." |
| 1452 | Proveedor o repuesto inexistente | `ValidacionException`: "El proveedor o el repuesto indicado no existe." |
| 3819 | Viola un `CHECK` | `ValidacionException` |
| 1264 | Número fuera de rango | `ValidacionException` |
| 1406 | Texto demasiado largo | `ValidacionException` |
| Otro | Error técnico | `DaoException` con la causa |

## 7. Supuestos y pendientes

1. **Un lote puede traer varios repuestos** (así está el modelo de datos). El servicio lo
   soporta; el controlador de consola registra uno por ingreso.
2. **El lote es inmutable:** no hay editar ni borrar lotes. Una corrección se haría con otro
   ingreso o con un ajuste que defina el equipo. Pendiente de confirmar.
3. **Reglas de fechas supuestas** (el enunciado no las concreta): el ingreso no puede ser
   futuro, la caducidad no puede ser anterior al ingreso y se permite registrar un lote ya
   vencido (por ejemplo, datos históricos); FEFO nunca lo usa.
4. **El repuesto se valida por la base** (clave foránea), no con una consulta previa. El
   proveedor sí se valida con `ProveedorConsulta` (doble en memoria, reemplazable).
5. **El stock físico incluye los lotes vencidos**, igual que en `data.sql` y en el filtro de
   stock bajo del módulo Repuesto. FEFO solo considera lotes vigentes.
6. **Descontar y reservar son del módulo de reservas.** Este módulo propone (FEFO) y consulta;
   no modifica `cantidad_actual` después del ingreso.
7. **El control de transacciones está repetido** en `LoteService` y `RepuestoService` (métodos
   privados `leer` y `enTransaccion`); se puede extraer a una clase común en la integración.

## 8. Cómo probar

Requisitos: MySQL encendido con `schema.sql` y `data.sql` cargados.

```sh
mvn test
```

| Clase de prueba | Qué comprueba | ¿Necesita MySQL? |
|---|---|---|
| `PoliticaFefoTest` (10) | Orden por caducidad, sin caducidad al final, desempates, reparto entre lotes, exclusión de vencidos (vence hoy sí sirve), sin disponibles, stock insuficiente, cantidad inválida | No |
| `LoteServiceTest` (17) | Código vacío o largo, proveedor inválido o inexistente, fechas inválidas (sin fecha, futura, caducidad anterior), sin líneas, cantidad cero o negativa, costos inválidos, repuesto repetido, datos de FEFO inválidos; todo rechazado antes de abrir conexión | No |
| `LoteIntegracionTest` (10) | Ingreso sin caducidad trazable y que suma al stock, ingreso con varias líneas, código duplicado sin guardar nada, `rollback` si falla una línea, trazabilidad por repuesto y por proveedor, stock calculado desde lotes, FEFO sobre `data.sql` | Sí (se omite sola si no hay servidor) |

Las pruebas de integración limpian lo que crean y nunca ejecutan `schema.sql`.