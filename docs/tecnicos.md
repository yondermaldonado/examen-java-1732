# Tarea 04 — Registro y consulta de técnicos

## Capas

- `Tecnico`: datos según la tabla tecnicos (id, nombre, documento_identidad,
  telefono, especialidad y activo). Por defecto se registra activo.
- `TecnicoDAO` / `TecnicoDAOJdbc`: CRUD, búsqueda por documento y comprobación de
  relaciones en servicios_tecnicos. SQL parametrizado; no confirma ni cierra la
  conexión del llamador.
- `TecnicoDAOFactory`: reutiliza Factory Method de la tarea 03.
- `TecnicoService`: validaciones, normalización, transacciones, unicidad y
  restricciones de eliminación.
- `TecnicoController`: adapta parámetros de la futura consola y devuelve mensajes;
  no lee de teclado, no imprime y no contiene SQL.

## Armado para la futura aplicación

```java
TecnicoService servicio = new TecnicoService(
    new TecnicoDAOFactory(),
    () -> ConexionFactory.getInstancia().crearConexion());
TecnicoController controlador = new TecnicoController(servicio);
String resultado = controlador.registrar("Ana Pérez", "123456", "3001234567", "Mecánica");
```

La vista mostrará el resultado. La conexión sigue apuntando al MySQL local con
las credenciales en ConexionFactory, sin variables de entorno ni properties.

## Operaciones y reglas

- Registrar, listar, buscar por id/documento, actualizar y eliminar.
- Nombre y documento obligatorios; teléfono y especialidad opcionales.
- Longitudes: nombre 255, documento/teléfono 20 y especialidad 150, como el SQL.
- Se recortan espacios en extremos; opcionales vacíos se guardan como NULL.
- Documento único incluso en actualización (se admite mantener el del mismo id).
- Se puede desactivar con actualizar(..., false), conservando su historial.
- No se elimina si tiene servicios_tecnicos; se recomienda desactivar.
- La FK y la UK de MySQL siguen siendo la garantía frente a operaciones
  concurrentes; sus errores se traducen en mensajes de negocio.
- Cada escritura confirma o revierte con una sola conexión. Las consultas no
  abren una transacción de escritura. Las asignaciones de técnicos a órdenes
  pertenecen a su tarea posterior y no se implementan aquí.

## Pruebas

`mvn verify` ejecuta pruebas de servicio/controlador con DAO de memoria y
conexiones controladas: validaciones, duplicados, actualización, desactivación,
eliminación restringida, commit/rollback y mensajes de error.

`TecnicoDAOJdbcIntegracionTest` comprueba el CRUD y las consultas añadidas en
MySQL local cuando está disponible. Usa rollback y no ejecuta schema.sql ni borra
la base. Si el servidor no está disponible, se omite explícitamente.

La tarea 04 parte del commit local de Factory Method de la tarea 03. No es
necesario duplicar la entidad ni su DAO; esta rama amplía sus consultas.
