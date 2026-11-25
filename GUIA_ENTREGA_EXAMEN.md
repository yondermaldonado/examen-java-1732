# Examen 1732 | Alternativa B

## Objetivo y recorrido
- Java 17 / consola / Maven / MySQL, sin cambiar el esquema.
- `com.carmotors.Main` → rol Administrador → Atención y órdenes de servicio → Completar Orden de Servicio.
- Estado válido: En Proceso → Completado; primero se confirma MySQL y después se notifica.
- Se imprime `LOG: El servicio # ID para el vehículo PLACA ha sido completado.`
- Se crea o amplía `notificaciones_clientes.txt` en la raíz de ejecución (UTF-8).
- Para verificar sin MySQL ejecuta `com.carmotors.PruebaObserver1732` desde las pruebas.

## Publicación (conforme a la guía del profesor)
Repositorio personal **público** `examen-java-1732`; desarrollo en **main**.
README al final con nombre, grupo, origen, menú y clases. Entregar URL del repositorio.
El ZIP no trae `.git` y no hay commit nuevo publicado por este proceso.

## Limitaciones y verificación
El paquete es una adaptación del ZIP adjunto, no es una extracción íntegra del
commit 66922e4ab46eae2cd9921995ce34c2902d6ef821.
Consulta `INFORME_ARCHIVOS.md` para las diferencias documentadas.
Usa CARMOTORS_DB_PASSWORD para conectar con MySQL.
