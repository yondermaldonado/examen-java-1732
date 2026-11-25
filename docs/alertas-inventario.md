# Issue #34 - Alertas de inventario usando Observer

## 1. Objetivo

Avisar cuando un repuesto tiene pocas unidades disponibles o cuando un lote esta por vencer.
Este modulo no reserva, no retira mercancia y no envia correos ni mensajes.

## 2. Clases principales

- `ObservadorInventario`: interfaz con el metodo `inventarioCambio(int repuestoId)`.
- `PublicadorInventario`: guarda los observadores y los notifica.
- `AlertasInventarioService`: implementa el observador y calcula las alertas.
- `AlertasDAOJdbc`: consulta `id`, `nombre` y `stock_minimo` de los repuestos.
- `ExistenciasService`: calcula las cantidades fisicas, reservadas y disponibles.
- `AlertasInventarioController`: devuelve un texto para mostrar en consola.

## 3. Que pasa al registrar un lote

1. `LoteService` valida los datos.
2. El DAO guarda el lote y sus lineas dentro de una transaccion.
3. Si todo sale bien se ejecuta `commit()`.
4. SOLO despues del commit, `LoteService` llama a `publicador.publicar(repuestoId)`.
5. El publicador llama a `inventarioCambio(repuestoId)` de sus observadores.
6. `AlertasInventarioService` vuelve a calcular las alertas.

Si ocurre `rollback()`, no se ejecuta `publicar()`.

`RepuestoService` utiliza el mismo mecanismo al crear, actualizar o eliminar.
Debe usarse la MISMA instancia del publicador en todos los servicios.

## 4. Reglas de las alertas

### Stock bajo

Se genera si `disponibleUtilizable < stockMinimo`.
La cantidad disponible utilizable tiene en cuenta las reservas activas y excluye lotes vencidos.

Ejemplo: si hay 10 unidades utilizables y el minimo es 12, aparece la alerta.

### Caducidad proxima

Se genera cuando un lote:

- Tiene fecha de caducidad.
- Tiene al menos una unidad fisica.
- No esta vencido.
- Vence hoy o dentro del numero de dias definido (`diasAviso`).

Si `diasAviso` es 7, se consideran las fechas desde hoy hasta dentro de 7 dias, inclusive.
Un lote sin caducidad no genera esta alerta; uno ya vencido tampoco cuenta como 'proximo'.
La fecha de hoy se proporciona con `Supplier<LocalDate>` para poder hacer pruebas estables.

## 5. Consultas y reporte

- `consultarAlertas()`: calcula la lista actual.
- `ultimasAlertas()`: devuelve la ultima lista calculada.
- `reporteConsola()`: devuelve un texto que se puede imprimir con `System.out.println`.

El reporte se vuelve a calcular al consultarlo, aunque no se haya recibido un evento.
Los eventos se mantienen en memoria; no son mensajes persistentes.

## 6. Como probar

```bash
mvn -Dtest=AlertasInventarioServiceTest test
mvn -Dtest=ObserverIntegracionTest test
mvn test
```

Las pruebas unitarias usan listas en memoria. No utilizan `Proxy.newProxyInstance()`.
La prueba de integracion requiere MySQL; si no se puede conectar, se omite.
Comprobar en IntelliJ que aparezca 'passed' y NO 'skipped'.

## 7. Limites y decisiones

- El modulo no modifica stock ni reservas. Eso corresponde a otros modulos.
- Un SQL ejecutado directamente en MySQL no genera un evento Observer de Java.
- En el futuro, el modulo de ordenes debera publicar sus cambios DESPUES de confirmar su transaccion.
- Si un observador falla, se informa por la consola de errores y los siguientes siguen siendo notificados.
- El codigo prioriza `if`, `for` y metodos separados, aunque ocupe mas lineas.

## 8. Como leer el codigo (orden recomendado)

1. `modelo/RepuestoMinimo.java`: modelo con `private`, constructor y getters.
2. `modelo/AlertaInventario.java`: modelo con constructor, getters y `toString()`.
3. `servicio/ObservadorInventario.java`: interfaz del observador.
4. `servicio/PublicadorInventario.java`: suscribe, desuscribe y avisa a los observadores.
5. `dao/AlertasDAO.java` y `dao/AlertasDAOJdbc.java`: leen minimos de MySQL.
6. `servicio/AlertasInventarioService.java`: aplica reglas de stock y caducidad.
7. `controlador/AlertasInventarioController.java`: solicita el reporte para la consola.
8. `servicio/LoteService.java` y `servicio/RepuestoService.java`: publican despues de confirmar.
9. Pruebas: simulan casos y verifican las reglas.

**Recorrido:** servicios de lotes/repuestos -> commit -> publicador -> observador
-> consultas DAO y ExistenciasService -> lista de alertas -> controlador -> consola.

Los modelos `AlertaInventario` y `RepuestoMinimo` ya NO usan `record`.
El modulo previo de existencias conserva sus propios tipos (`StockLote`, `StockRepuesto`).
