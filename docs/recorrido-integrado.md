# Tarea 09 — Verificar transacciones y recorrido integrado

## Alcance real

Esta rama parte del commit LOCAL de la tarea 08. No modifica el repositorio remoto.
El recorrido prueba servicios/DAO reales sobre MySQL, usando los contratos ya
acordados. Se conservan las pruebas transaccionales y concurrentes de las tareas
anteriores, en lugar de duplicarlas.

El recorrido usa `FacturacionService` con tasa de fixture de 19 %. Para probar
el rollback se inyecta un fallo de commit después de insertar la factura y
actualizar el estado en MySQL. La tasa definitiva y la exportación TXT quedan
fuera de este recorrido.

El catálogo de operaciones usa un detalle de mano de obra de fixture. El proveedor
se consulta mediante un doble del puerto ProveedorConsulta con ID conocido en
los datos de prueba. No se implementan aquí módulos pendientes de compañeros.

## Preparación

- JDK 17 y Maven; MySQL local, con schema.sql/data.sql de esta rama preparados.
- Mantener los catálogos/fixtures básicos (modelo, proveedor, tipo, marca y
  operación con ID 1), usados por el escenario reproducible.
- ConexionFactory conserva URL/usuario/contraseña en código, como se acordó.
- Si hay datos valiosos, NO ejecutar schema.sql: ese archivo recrea la base.
  Para bases con estados anteriores, aplicar actualizar-estados-ordenes.sql.

Las pruebas crean y limpian filas propias; no recrean la base ni eliminan datos
ajenos. AUTO_INCREMENT puede avanzar aunque los fixtures se borren/viertan: eso
es normal. Ejecutar siempre en la base de desarrollo/pruebas, no en producción.

## Comandos reproducibles

Desde la raíz, comprobar `java -version` y `mvn -version` (JDK 17).

```sh
mvn clean test
mvn -DskipTests package
```

El segundo comando evita repetir las pruebas ya aprobadas. Para ejecutar ambas
fases de una sola vez se puede usar `mvn clean verify`.

Ver ayuda del ejecutable de integración sin acceder a MySQL:

```sh
mvn test-compile exec:java -Dexec.mainClass=com.carmotors.integracion.RecorridoIntegrado -Dexec.classpathScope=test
```

Ejecutar el recorrido real contra MySQL local preparado:

```sh
mvn test-compile exec:java -Dexec.mainClass=com.carmotors.integracion.RecorridoIntegrado -Dexec.classpathScope=test -Dexec.args=--ejecutar
```

Se agregó exec-maven-plugin con versión fijada. El ejecutable está en TEST y no
se incluye en el JAR normal; no reemplaza el Main/menú de consola final pendiente.
El argumento explícito --ejecutar evita crear fixtures al solicitar solo ayuda.

## Recorrido y resultados esperados

1. Registrar cliente nuevo mediante ClienteService y DAO reales.
2. Registrar vehículo asociado a ese cliente con VehiculoService.
3. Registrar técnico, repuesto e ingreso de lote con sus servicios/DAO reales.
4. Crear orden Pendiente usando el vehículo y consultar su propietario.
5. Asignar técnico: la orden pasa automáticamente a En Proceso. Registrar 02:30 horas.
6. Preparar operación de mano de obra por 12000.00 (fixture de catálogo).
7. Reservar cuatro unidades de diez: físico 10, reservas 4, disponible 6.
8. Completar la orden ya iniciada: físico 6, reservas activas 0, estado Completado.
9. Repetir completar: rechazo y ningún descuento adicional.
10. Insertar la factura, actualizar el estado y provocar un fallo de commit: rollback; factura ausente,
    estado Completado y stock 6.
11. Lanzar dos intentos concurrentes: uno confirma una factura de 14756.00
    (400.00 de repuestos históricos, 12000.00 de mano de obra y 2356.00 de impuesto).
    El otro intento se rechaza. El precio vigente del repuesto se cambió antes
    de facturar para comprobar que se conserva el precio histórico.
12. Repetir facturar: rechazo, sin nueva factura ni consumo de inventario.
13. Entregar: estado Entregado y fecha_entrega persistidos.
14. Eliminar únicamente los fixtures propios en orden de FK, incluso si falla
    el escenario. No se imprime ninguna contraseña.

Salida resumida verificada:

```text
RESULTADO: Entregado | stock 10 → 6 | factura de prueba 14756.00
```

## Matriz de comprobaciones

| Regla | Evidencia automatizada |
|---|---|
| Dos órdenes no sobre-reservan | ReservasOrdenIntegracionTest, dos hilos y conexiones reales |
| Stock insuficiente revierte el ajuste | ReservasOrdenIntegracionTest |
| Finalización concurrente descuenta una vez | ReservasOrdenIntegracionTest |
| Fallo intermedio revierte varios lotes y estado | ReservasOrdenIntegracionTest |
| Estados inválidos/repetidos se rechazan | TransicionesOrdenServiceTest y recorrido real |
| Asignaciones N:M, duplicados y cerradas | AsignacionTecnicoServiceTest + ServicioTecnicoDAOJdbcIntegracionTest |
| Conexión del llamador y rollback JDBC | Pruebas DAO de técnicos, órdenes y asignaciones |
| Fallo de commit revierte factura y estado; dos intentos dejan una factura | RecorridoIntegradoTest en MySQL |
| Cliente/vehículo/orden son coherentes | RecorridoIntegradoTest |
| Precio y mano de obra históricos | ReservasOrdenIntegracionTest + OrdenServicioDAOJdbcIntegracionTest |
| Observadores se notifican tras commit | ObserverIntegracionTest + pruebas de reservas/transiciones |

## Ejecución histórica del 17 de noviembre de 2026 con el doble de facturación

Entorno: JDK 17, Maven 3.9.9, MySQL 8.4 en contenedor temporal aislado.

- `mvn clean test`: 124 pruebas, 0 fallos, 0 errores, 0 omitidas.
- `mvn -DskipTests package`: BUILD SUCCESS, JAR generado.
- Arranque por exec:java del recorrido: BUILD SUCCESS, estado Entregado y stock 6.
- Conteos de las 23 tablas antes/después de suite y recorrido: sin diferencias.
- Los fixtures del servidor temporal se prepararon desde SQL del proyecto; no
  se modificaron los scripts/credenciales para el contenedor. El host del usuario
  se adaptó solo dentro de ese servidor aislado para acceso desde su puente.

No se versionan logs generados, JAR, target ni configuración de Docker. Cuando
MySQL no está disponible, las pruebas JDBC existentes se omiten expresamente;
una ejecución con omitidas NO equivale a esta validación real.

## Pendientes para validar el producto completo

- Confirmar la tasa tributaria definitiva e incorporar la exportación TXT con sus comprobaciones.
- Sustituir el proveedor de fixture por el servicio real cuando esté integrado.
- Incorporar registro real de operaciones del catálogo en lugar del detalle de fixture.
- Integrar Main, vistas y navegación final y repetir la demostración con entrada
  por consola. Esta tarea comprueba el arranque del verificador, no certifica una
  interfaz final que todavía no existe.
- Conectar reportes gerenciales e historial/recordatorios al recorrido cuando sus
  módulos estén disponibles, evitando marcar esas funciones como verificadas aquí.
