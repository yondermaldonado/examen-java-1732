# Tarea 06 — Asignación de técnicos a órdenes

El módulo reutiliza servicios, tecnicos y
servicios_tecnicos del esquema aprobado, sin agregar tablas o IDs artificiales.

## Componentes

- ServicioTecnico: relación identificada por (servicioId, tecnicoId), con horas
  BigDecimal y datos de consulta de técnico/estado.
- ServicioTecnicoDAO/Jdbc: asignar, comprobar existencia, retirar, actualizar
  horas, listar por orden y listar por técnico; PreparedStatement en todas las
  consultas. Las consultas de referencias con FOR UPDATE permiten validar y
  escribir en una misma unidad de trabajo.
- ServicioTecnicoDAOFactory: Factory Method.
- AsignacionTecnicoService: reglas, bloqueos y transacciones.
- AsignacionTecnicoController: adapta texto y devuelve mensajes a la futura vista.

## Reglas

Varios técnicos pueden estar en una orden y un técnico en varias órdenes.
No se imponen exclusividades ni agendas que el enunciado no solicita.

Para asignar, orden y técnico deben existir; el técnico debe estar activo.
Al asignar un técnico a una orden Pendiente, esta pasa automáticamente a En Proceso.
La asignación y el cambio de estado se confirman juntos; si alguno falla se
revierte toda la operación. Añadir más técnicos a una orden En Proceso conserva
ese estado. Retirar técnicos no devuelve automáticamente la orden a Pendiente.
No se repite la pareja (servicio, técnico), protegida también por la PK de MySQL.
Solo Pendiente y En Proceso admiten asignar, retirar o cambiar horas; Completado,
Facturado y Entregado conservan sus responsables e historial.

En una asignación existente se pueden retirar técnicos inactivos o registrar
sus horas mientras la orden esté abierta. No se modifica su estado de actividad.
El menú registra duraciones con `registrarHoras(orden, tecnico, "02:30")` del
controlador. Cada registro **suma** al acumulado de esa pareja orden/técnico:
02:30 + 01:45 = 04:15. Repetir el registro suma nuevamente; no es una corrección
del total anterior. El servicio ofrece `registrarTiempo` para entradas HH:mm.
Los listados muestran HH:mm, admiten más de 24 horas y el máximo es 999:59.
Se rechazan minutos fuera de 00..59 y entradas decimales en el controlador.

La base conserva DECIMAL(5,2) y los datos existentes. Para sumar se recuperan
los minutos del total almacenado, se añaden los nuevos minutos y se convierte
una sola vez a centésimas de hora. Esto conserva exactamente cada minuto sin
acumular redondeos; valores antiguos se aproximan al minuto más cercano.
La lectura del acumulado usa FOR UPDATE dentro de la transacción para evitar
pérdidas cuando dos registros coinciden. La API interna BigDecimal se conserva
para consumidores anteriores, pero ahora también acumula.

`listarCargaEnProceso()` del controlador/servicio lista técnicos y cantidad de
órdenes exclusivamente En Proceso. `listarSinOrdenesAbiertas()` lista quienes
no tienen órdenes Pendiente ni En Proceso, aunque tengan trabajos terminados.
Ambas consultas incluyen técnicos inactivos y muestran su condición; estar sin
órdenes no habilita asignaciones a un técnico inactivo. Un técnico con órdenes
pendientes solamente no aparece en ninguna lista. El menú debe ofrecer estas
dos consultas, pedir orden/técnico/tiempo adicional HH:mm para registrar y luego
mostrar `listarPorOrden` o `listarPorTecnico` para consultar el acumulado.

Las escrituras bloquean primero la orden y después el técnico con SELECT FOR
UPDATE, validan y operan sobre la misma conexión. Commit/rollback y cierre son
responsabilidad del servicio. Los próximos cambios de estado de órdenes deberán
respetar el mismo bloqueo de su fila para coordinarse con estas operaciones.
Las transiciones, el consumo y la facturación se gestionan en sus servicios respectivos.

## Uso

```java
AsignacionTecnicoService servicio = new AsignacionTecnicoService(
    new ServicioTecnicoDAOFactory(),
    () -> ConexionFactory.getInstancia().crearConexion());
AsignacionTecnicoController controlador = new AsignacionTecnicoController(servicio);
String resultado = controlador.asignar("1", "2");
String horas = controlador.registrarHoras("1", "2", "02:30");
String responsables = controlador.listarPorOrden("1");
```

La vista muestra esos resultados. listarPorTecnico permite consultar las
participaciones/horas que necesitará el módulo de productividad.

## Pruebas

`mvn verify`: cardinalidad N:M, duplicados, inexistentes/inactivos, órdenes
cerradas, horas exactas, consultas, fallos SQL, rollback y mensajes con dobles.
ServicioTecnicoDAOJdbcIntegracionTest verifica FK/PK, consultas y rollback con
MySQL local si está disponible; no recrea la base ni borra fixtures ajenos.
Requiere vehículos/catálogos cargados y estados de la tarea 05 actualizados.
La prueba de MySQL se omite explícitamente si no hay servidor. No se afirma
haber probado concurrencia real solo por pasar pruebas con dobles.
