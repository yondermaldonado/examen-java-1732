# Módulo Repuesto

CRUD y filtros del catálogo de repuestos de CarMotors. Sigue MVC: el controlador
convierte texto, el servicio valida y controla la transacción, y el DAO es el único
lugar con SQL. Java 17, JDBC con `PreparedStatement` y `BigDecimal` para el dinero.

```
RepuestoController  ->  RepuestoService  ->  RepuestoDAO (RepuestoDAOJdbc)  ->  MySQL
   (texto y mensajes)     (reglas y commit)        (todo el SQL)
                              |
                       ProveedorConsulta (interfaz; doble en memoria)
```

| Capa | Clase | Paquete |
|---|---|---|
| Entidad | `Repuesto` | `com.carmotors.modelo` |
| DAO | `RepuestoDAO`, `RepuestoDAOJdbc` | `com.carmotors.dao` |
| Servicio | `RepuestoService`, `ProveedorConsulta`, `ProveedorConsultaMemoria` | `com.carmotors.servicio` |
| Controlador | `RepuestoController` | `com.carmotors.controlador` |
| Excepciones | `ValidacionException`, `DaoException` (y `ConexionException` del módulo de conexión) | `com.carmotors.excepcion` |

## 1. Entidad `Repuesto`

Una instancia representa **una fila del catálogo**: un repuesto junto con **uno** de sus
proveedores y el costo de ese proveedor.

| Campo | Tipo Java | Origen en la base de datos | Notas |
|---|---|---|---|
| `id` | `int` | `repuestos.id` | Lo genera MySQL al crear. Obligatorio (mayor que 0) al actualizar. |
| `nombre` | `String` | `repuestos.nombre` (`VARCHAR(255)`) | Obligatorio. |
| `tipoId` | `int` | `repuestos.repuesto_tipo_id` | Obligatorio. Debe existir en `repuesto_tipos`. |
| `marcaId` | `int` | `repuestos.marca_id` | Obligatorio. Debe existir en `marcas_repuesto`. |
| `proveedorId` | `int` | `proveedores_repuestos.proveedor_id` | Obligatorio. Al leer, vale 0 si el repuesto no tiene proveedor. |
| `precioCompra` | `BigDecimal` | `proveedores_repuestos.costo` | Costo de ese proveedor. `DECIMAL(10,2)`. |
| `precioVenta` | `BigDecimal` | `repuestos.precio_venta` | `DECIMAL(10,2)`. |
| `stockMinimo` | `int` | `repuestos.stock_minimo` | Mayor o igual que 0. Valor provisional, ver supuestos. |
| `tipo` | `String` | `repuesto_tipos.nombre` | Solo para mostrar; lo llena el DAO al leer. |
| `marca` | `String` | `marcas_repuesto.nombre` | Solo para mostrar; lo llena el DAO al leer. |
| `proveedor` | `String` | `proveedores.nombre` | Solo para mostrar; lo llena el DAO al leer. |

La tabla `repuestos` no guarda proveedor ni precio de compra: viven en
`proveedores_repuestos` (relación N:M con `costo`). Por eso un repuesto con varios
proveedores aparece **una vez por proveedor** en los listados.

## 2. Contratos

### 2.1 DAO (`RepuestoDAO`)

Todos los métodos reciben la `Connection`. El DAO **no la cierra ni hace commit o
rollback**: eso lo hace el servicio.

| Método | Recibe | Devuelve | Excepciones |
|---|---|---|---|
| `crear(con, repuesto)` | Repuesto ya validado | Id generado | `ValidacionException` (errores conocidos, ver sección 4), `DaoException` (otros) |
| `actualizar(con, repuesto)` | Repuesto con `id` | `true` si el repuesto existía, `false` si no | Igual que `crear` |
| `eliminar(con, id)` | Id | `true` si se borró el repuesto, `false` si no existía | `ValidacionException` si tiene lotes, reservas o servicios asociados |
| `listar(con)` | — | Todas las filas del catálogo | `DaoException` |
| `buscarPorId(con, id)` | Id | Filas de ese repuesto (una por proveedor); lista vacía si no existe | `DaoException` |
| `buscarPorNombre(con, texto)` | Texto | Filas cuyo nombre contiene el texto | `DaoException` |
| `listarPorTipo(con, tipoId)` | Id de tipo | Filas de ese tipo | `DaoException` |
| `listarPorMarca(con, marcaId)` | Id de marca | Filas de esa marca | `DaoException` |
| `listarPorProveedor(con, proveedorId)` | Id de proveedor | Filas vendidas por ese proveedor | `DaoException` |
| `listarStockBajo(con)` | — | Filas cuyo stock físico es menor que `stock_minimo` | `DaoException` |

`crear`, `actualizar` y `eliminar` escriben en **dos tablas** (`repuestos` y
`proveedores_repuestos`):

- `crear`: inserta el repuesto y luego el vínculo con su proveedor.
- `actualizar`: actualiza el repuesto y el costo de ese proveedor; si ese proveedor aún
  no vendía el repuesto, inserta el vínculo.
- `eliminar`: borra primero los vínculos y luego el repuesto.

### 2.2 Servicio (`RepuestoService`)

Se construye con `RepuestoService(RepuestoDAO dao, ProveedorConsulta proveedores, Supplier<Connection> conexiones)`.
En producción la conexión sale de `ConexionFactory`:

```java
new RepuestoService(new RepuestoDAOFactory().obtenerDAO(), new ProveedorConsultaMemoria(),
        () -> ConexionFactory.getInstancia().crearConexion());
```

| Método | Devuelve | Notas |
|---|---|---|
| `crear(Repuesto)` | Id generado | Valida, abre transacción, `commit` si todo sale bien. |
| `actualizar(Repuesto)` | `boolean` | Exige `id` mayor que 0. Misma transacción. |
| `eliminar(int id)` | `boolean` | Exige `id` mayor que 0. Si falla, `rollback`: los vínculos del repuesto quedan intactos. |
| `listar()` | `List<Repuesto>` | |
| `buscarPorId(int)` | `List<Repuesto>` | Lista vacía si no existe. |
| `buscarPorNombre(String)` | `List<Repuesto>` | `null` se trata como texto vacío (devuelve todo). |
| `listarPorTipo(int)`, `listarPorMarca(int)`, `listarPorProveedor(int)` | `List<Repuesto>` | |
| `listarStockBajo()` | `List<Repuesto>` | |

Reglas de conexión:

- Las **validaciones se hacen antes de abrir la conexión**: un dato inválido nunca toca
  la base de datos.
- El servicio abre y cierra la conexión (`try-with-resources`). Los cambios usan
  `setAutoCommit(false)`, `commit()` y `rollback()`.
- Los errores de conexión se propagan como `ConexionException`.

### 2.3 Controlador (`RepuestoController`)

Recibe `String` (lo que escribe el usuario), los convierte y devuelve **un mensaje listo
para mostrar**. Nunca lanza excepciones hacia la vista.

| Método | Mensaje de éxito |
|---|---|
| `crear(nombre, tipoId, marcaId, proveedorId, precioCompra, precioVenta, stockMinimo)` | `Repuesto creado con id N.` |
| `actualizar(id, nombre, tipoId, marcaId, proveedorId, precioCompra, precioVenta, stockMinimo)` | `Repuesto actualizado.` o `No existe un repuesto con ese id.` |
| `eliminar(id)` | `Repuesto eliminado.` o `No existe un repuesto con ese id.` |
| `listar()`, `buscarPorId(id)`, `buscarPorNombre(texto)`, `listarPorTipo(id)`, `listarPorMarca(id)`, `listarPorProveedor(id)`, `listarStockBajo()` | Una línea por fila: `id \| nombre \| tipo \| marca \| proveedor \| compra \| venta \| mínimo`, o `No se encontraron repuestos.` |

Mensajes de error:

| Situación | Mensaje |
|---|---|
| Texto que no es número (por ejemplo `abc` o `25000,50`) | `Error: escribe números válidos (ids y cantidades enteros; precios con punto, por ejemplo 25000.50).` |
| `ValidacionException` | `Error: <mensaje de la validación>` |
| `DaoException` o `ConexionException` | `Error del sistema: <mensaje>` |

## 3. Validaciones (servicio)

Todas lanzan `ValidacionException` con un mensaje claro.

| Campo | Regla |
|---|---|
| Repuesto | No puede ser nulo. |
| `nombre` | Obligatorio, no vacío ni solo espacios, máximo 255 caracteres. Se guarda sin espacios sobrantes. |
| `tipoId`, `marcaId` | Mayores que 0. |
| `proveedorId` | Mayor que 0 y debe existir según `ProveedorConsulta.existe(...)`. |
| `precioCompra`, `precioVenta` | Obligatorios, no negativos, máximo 2 decimales, máximo `99999999.99` (columna `DECIMAL(10,2)`). Se comparan con `compareTo`, nunca con `equals`. Se guardan con escala 2. |
| `stockMinimo` | Mayor o igual que 0. No hay otro límite porque el enunciado no define el mínimo. |
| `id` (actualizar y eliminar) | Mayor que 0. |

## 4. Errores de MySQL traducidos por el DAO

| Código MySQL | Cuándo ocurre | Excepción y mensaje |
|---|---|---|
| 1451 | Eliminar un repuesto con lotes, reservas o servicios | `ValidacionException`: "No se puede eliminar: el repuesto tiene lotes o servicios asociados." |
| 1452 | Tipo, marca o proveedor que no existen | `ValidacionException`: "El tipo, la marca o el proveedor indicado no existe." |
| 3819 | Se viola un `CHECK` (por ejemplo precio negativo) | `ValidacionException` |
| 1264 | Número fuera de rango | `ValidacionException` |
| 1406 | Texto demasiado largo | `ValidacionException` |
| Cualquier otro | Error técnico | `DaoException` con la causa original |

## 5. Consultas del catálogo

Consulta base (todos los filtros la reutilizan y le añaden un `WHERE`; el resultado se
ordena por `r.id, pr.proveedor_id`):

```sql
SELECT r.id AS id, r.nombre AS nombre,
       r.repuesto_tipo_id AS tipo_id, rt.nombre AS tipo,
       r.marca_id AS marca_id, mr.nombre AS marca,
       pr.proveedor_id AS proveedor_id, p.nombre AS proveedor,
       pr.costo AS precio_compra, r.precio_venta AS precio_venta,
       r.stock_minimo AS stock_minimo
FROM repuestos AS r
INNER JOIN repuesto_tipos AS rt ON rt.id = r.repuesto_tipo_id
INNER JOIN marcas_repuesto AS mr ON mr.id = r.marca_id
LEFT JOIN proveedores_repuestos AS pr ON pr.repuesto_id = r.id
LEFT JOIN proveedores AS p ON p.id = pr.proveedor_id
```

Filtros (el valor `?` siempre va como parámetro del `PreparedStatement`):

| Filtro | Condición añadida |
|---|---|
| Por id | `WHERE r.id = ?` |
| Por nombre | `WHERE r.nombre LIKE ?` (el parámetro es `"%" + texto + "%"`) |
| Por tipo | `WHERE r.repuesto_tipo_id = ?` |
| Por marca | `WHERE r.marca_id = ?` |
| Por proveedor | `WHERE pr.proveedor_id = ?` |
| Stock bajo | `WHERE (SELECT COALESCE(SUM(lr.cantidad_actual), 0) FROM lotes_repuestos AS lr WHERE lr.repuesto_id = r.id) < r.stock_minimo` |

El stock físico de un repuesto es la suma de `cantidad_actual` de todos sus lotes.

Sentencias de cambio:

```sql
-- crear
INSERT INTO repuestos (nombre, repuesto_tipo_id, marca_id, precio_venta, stock_minimo) VALUES (?, ?, ?, ?, ?)
INSERT INTO proveedores_repuestos (proveedor_id, repuesto_id, costo) VALUES (?, ?, ?)

-- actualizar
UPDATE repuestos SET nombre = ?, repuesto_tipo_id = ?, marca_id = ?, precio_venta = ?, stock_minimo = ? WHERE id = ?
UPDATE proveedores_repuestos SET costo = ? WHERE proveedor_id = ? AND repuesto_id = ?
-- si el segundo UPDATE no afecta filas, se inserta el vínculo (mismo INSERT de crear)

-- eliminar
DELETE FROM proveedores_repuestos WHERE repuesto_id = ?
DELETE FROM repuestos WHERE id = ?
```

## 6. Supuestos y pendientes

1. **Un repuesto con varios proveedores aparece varias veces.** Una fila del catálogo es el
   par (repuesto, proveedor). Con `data.sql` hay 12 repuestos y 17 filas, porque 5
   repuestos tienen dos proveedores.
2. **`ProveedorConsultaMemoria` es un doble temporal** que conoce los proveedores 1 a 4 de
   `data.sql`. Cuando exista el módulo Proveedor, se escribe otra implementación de
   `ProveedorConsulta` y el servicio no cambia.
3. **`stock_minimo` es provisional.** El enunciado no concreta la cantidad mínima; queda
   pendiente de consultar al docente. El código solo exige que no sea negativo.
4. **Regla precio de venta mayor o igual al de compra: pendiente.** No se valida porque no
   está definida; los datos de `data.sql` ya la cumplen.
5. **`ConexionFactory` entrega conexiones nuevas.** El servicio las cierra y controla la
   transacción; el DAO nunca hace commit ni cierra.
6. **Datos de ejemplo:** `database/schema.sql` y `database/data.sql` (fecha de referencia
   2026-11-13).

## 7. Cómo probar

Requisitos: MySQL encendido con `schema.sql` y `data.sql` cargados.

```sh
mvn test
```

| Clase de prueba | Qué comprueba | ¿Necesita MySQL? |
|---|---|---|
| `RepuestoServiceTest` | 14 casos inválidos (precios negativos, 3 decimales, nulo, demasiado grande, nombre vacío o largo, tipo o marca inválidos, proveedor inexistente, stock negativo, ids inválidos) rechazados antes de abrir conexión | No |
| `RepuestoIntegracionTest` | Catálogo de 17 filas, filtros, stock bajo (solo los discos de freno, id 6), ciclo crear-buscar-actualizar-eliminar, rechazo de tipo inexistente sin guardar nada, y que eliminar el repuesto 1 se rechaza sin perder sus vínculos (`rollback`) | Sí (se omite sola si no hay servidor) |

Las pruebas nunca ejecutan `schema.sql`; el ciclo de crear y eliminar limpia lo que crea.