# Tarea 05 — Alta y consulta de órdenes

Esta rama parte del commit local de técnicos (tarea 04), que a su vez incluye
Factory Method (tarea 03). No añade asignaciones, consumo, facturación ni
transiciones de estado: corresponden a tareas posteriores.

## Componentes

OrdenServicio, OrdenServicioDAO, OrdenServicioDAOJdbc,
OrdenServicioDAOFactory, OrdenServicioService y OrdenServicioController.
El DAO recibe la Connection del servicio y no hace commit ni la cierra.
Las consultas devuelven vehículo, placa y cliente a través de la relación
vehiculos.cliente_id; no se agrega cliente_id a servicios.

## Alta y consultas

Crear con vehículo existente, Preventivo o Correctivo y descripción no vacía.
El servicio fija Pendiente, fecha de recepción con Clock inyectable y fechas de
completado/entrega nulas. No se reciben estado ni fechas posteriores del usuario.
Se consulta por id, vehículo y estado, y se listan todas las órdenes.

La mano de obra es BigDecimal y se deriva de SUM(servicios_detalle.precio_cobrado),
con cero cuando no hay detalles. La tabla servicios no tiene costo_mano_obra.
No se inventa esa columna ni se cobra un importe separado que duplique detalles.
Las restricciones DECIMAL/no negativo del esquema siguen vigentes para las
operaciones del catálogo, que se implementarán en su tarea.

## Integración con vehículos

Se reutiliza el puerto VehiculoConsulta y el adaptador VehiculoConsultaImpl ya
presentes. No se duplica ni se modifica el módulo del compañero.

```java
OrdenServicioService servicio = new OrdenServicioService(
    new OrdenServicioDAOFactory(),
    new VehiculoConsultaImpl(new VehiculoDAOFactory().obtenerDAO()),
    () -> ConexionFactory.getInstancia().crearConexion(), Clock.systemDefaultZone());
OrdenServicioController controlador = new OrdenServicioController(servicio);
String resultado = controlador.crear("1", "Preventivo", "Revisión de aceite");
```

El cliente se identifica por el vehículo y no se introduce otro propietario en
la orden. La FK protege el alta si el vehículo desaparece después de comprobarlo.

## Estados y datos de ejemplo

El data.sql previo usaba Recibido/En diagnóstico/En reparación, mientras el
requisito exige Pendiente/En Proceso/Completado/Facturado/Entregado.
Esta tarea corrige los nombres en data.sql y conserva el sentido de sus órdenes
activas. Los IDs no representan el orden de transición; el DAO resuelve por nombre.

Para una base YA cargada, ejecutar una sola vez (también es repetible):

```sql
SOURCE database/actualizar-estados-ordenes.sql;
```

Este script conserva tablas y órdenes, remapea estados antiguos y carga tipos
faltantes. No ejecuta schema.sql ni elimina órdenes. Para instalaciones nuevas,
usar schema.sql y data.sql actualizados. No ejecutar schema.sql sobre datos que
se quieran conservar porque recrea la base.

## Verificación

`mvn verify` prueba alta en Pendiente, fecha fija, cliente del vehículo,
validaciones, consultas, errores y commit/rollback mediante dobles independientes.
La integración JDBC usa MySQL local si está disponible y revierte sus propias
órdenes/detalles de prueba. Requiere fixtures y el catálogo de estados actualizado;
no recrea ni borra la base. Si no hay servidor MySQL, se omite explícitamente.
