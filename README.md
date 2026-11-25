# proyecto_java_grupo01

## Reporte de servicios más solicitados

Con [MySQL local configurado](docs/configuracion-jdbc.md) y los datos de
`database/data.sql` cargados, ejecuta desde la raíz del proyecto:

```sh
mvn compile exec:java -Dexec.mainClass=com.carmotors.ReporteServiciosConsola -Dexec.args="2026-11-01 2026-11-30"
```

El reporte cuenta las órdenes por tipo de mantenimiento según su fecha de
recepción. Ambas fechas se incluyen y se cuentan las órdenes de cualquier
estado. Por defecto se muestran primero los tipos con más solicitudes.
Como tercer argumento se puede indicar `CANTIDAD_DESC`, `CANTIDAD_ASC` o
`NOMBRE_ASC`.

El catálogo inicial distingue mantenimiento preventivo y correctivo. Cualquier
ampliación a servicios específicos del catálogo requiere un acuerdo del equipo.
Las cantidades reflejan demanda registrada. El reporte no calcula rentabilidad
porque no utiliza ingresos ni costos.

Para verificar la consulta JDBC y el reporte con un fixture independiente en
memoria (H2 en modo MySQL), ejecuta
`mvn -Dtest=ServiciosMasSolicitadosStrategyTest test`.

## Reporte de productividad por técnico

`ProductividadTecnicosStrategy` implementa `ReporteStrategy` con el tipo
`PRODUCTIVIDAD_TECNICOS`. Para integrarla con el módulo de reportes:

```java
ReporteStrategy estrategia = new ProductividadTecnicosStrategy();
ReporteDTO reporte = estrategia.generar(new FiltroReporte(
        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), OrdenReporte.CANTIDAD_DESC));
```

Se usa la fecha de completado y se incluyen ambos días del periodo. Las órdenes
facturadas o entregadas conservan su participación en el periodo en que se
completaron. Se excluyen las órdenes pendientes, en proceso o sin fecha de
completado. Cada técnico asignado suma una participación por orden. Una orden
con dos técnicos suma una participación a cada uno, por lo que la suma puede
superar el total de órdenes completadas. La métrica cuenta asignaciones
registradas, incluidas las de técnicos inactivos, e incluye al técnico aunque
su asignación tenga cero horas registradas.

El fixture propio se verifica con `mvn -Dtest=ProductividadTecnicosStrategyTest test`.

## Reporte de consumo de repuestos

`ConsumoRepuestosStrategy` implementa `ReporteStrategy` con el tipo
`CONSUMO_REPUESTOS`. Para integrarla con el módulo de reportes:

```java
ReporteStrategy estrategia = new ConsumoRepuestosStrategy();
ReporteDTO reporte = estrategia.generar(new FiltroReporte(
        LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), OrdenReporte.CANTIDAD_DESC));
```

Se suman las unidades de cada reserva en estado `CONSUMIDA` según su
`fecha_consumo`, con ambos días incluidos. Las reservas activas, liberadas
o sin fecha de consumo quedan fuera. Si un repuesto se consume desde varios
lotes, se acumulan las unidades de todas sus reservas consumidas. Cada fila
identifica el repuesto por su ID y usa el orden del filtro. En un empate de
cantidad se ordena por nombre y luego por código.

El fixture independiente se verifica con `mvn -Dtest=ConsumoRepuestosStrategyTest test`.

## Cálculo y detalle de factura

`CalculadorFactura` recibe un puerto `ConsultaOrdenCompletada` y la tasa
de impuesto como fracción. Por ejemplo, `0.19` representa 19 %. La tasa
vigente queda pendiente de confirmación del docente y el servicio no fija
un valor por defecto.

El adaptador del puerto debe tomar el precio histórico de
`servicios_repuestos.precio_unitario_venta` y el importe de cada operación de
`servicios_detalle.precio_cobrado`. El subtotal suma las unidades por su
precio histórico más la mano de obra. Los importes de entrada respetan los
dos decimales del esquema. Se aplica la tasa una sola vez sobre el subtotal
y se redondea el impuesto a dos decimales con `HALF_UP`. El total suma el
subtotal y el impuesto redondeado.

El calculador devuelve el detalle y los totales. La persistencia y el cambio
de estado pertenecen al flujo de facturación del taller. El doble en memoria
del puerto se ejecuta con `mvn -Dtest=CalculadorFacturaTest test`.

## Persistencia de factura y transición de la orden

Inyecta `new FacturacionService(tasaImpuesto)` como `FacturacionOrden` en el
constructor completo de `OrdenServicioService`. La operación pública sigue
siendo `facturarOrden(id)`. Bloquea la orden, exige estado Completado, inserta
la factura y actualiza a Facturado en una conexión y una transacción. El
esquema existente protege la unicidad por `facturas.servicio_id`. El descuento
de reservas ocurre durante `completarOrden`.

El recorrido en MySQL usa una tasa de fixture de 19 %. La tasa de operación
sigue pendiente de confirmación. Para repetirlo con `schema.sql` y `data.sql`
cargados en MySQL local, ejecuta `mvn -Dtest=RecorridoIntegradoTest test`.

### Examen final 1732 | Notificación al cliente con Observer (alternativa B)
Estudiante y grupo: COMPLETAR_NOMBRE | COMPLETAR_GRUPO  
Proyecto de origen: https://github.com/RickDslayer/proyecto_java_grupo01  
Commit de referencia indicado: `66922e4ab46eae2cd9921995ce34c2902d6ef821`  
Acceso: Administrador → Atención y órdenes de servicio → Completar Orden de Servicio  
Clases del examen: `src/main/java/com/carmotors/servicio/observer/Sujeto.java`,
`Observador.java`, `ObservadorLogServicio.java`, `ObservadorNotificacionCliente.java`;
`src/main/java/com/carmotors/modelo/EventoServicioCompletado.java`;
`src/main/java/com/carmotors/servicio/OrdenServicioService.java`;
`src/main/java/com/carmotors/Main.java`;
`src/main/java/com/carmotors/vista/VistaOrdenes.java`.

`OrdenServicioService` conserva los observadores y les comunica un
`EventoServicioCompletado` **después del commit**; la persistencia de MySQL y
la notificación no se confunden. El primer observador imprime en consola,
el segundo añade el borrador al archivo `notificaciones_clientes.txt`.
No se envían mensajes reales por SMS o WhatsApp. La alternativa B utiliza
LinkedHashSet sincronizado y BufferedWriter.

**Procedencia:** este paquete parte del ZIP original aportado por el estudiante,
no de una descarga completa del commit remoto. Se verificó que **172 de 203 blobs**
del commit de referencia coincidían por contenido con el ZIP de entrada; hay
31 divergencias (incluidas imágenes, README y código). No se afirma una
sincronización íntegra con ese commit. Consulta `INFORME_ARCHIVOS.md`.

**Configuración:** antes de conectar MySQL, configura `CARMOTORS_DB_PASSWORD`
en el entorno, como indica `docs/configuracion-jdbc.md`. Ejecuta
`com.carmotors.PruebaObserver1732` desde `src/test/java` para validar el patrón sin MySQL.
