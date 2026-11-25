# Conexión JDBC local

Cada integrante ejecuta su propio MySQL en localhost:3306 y mantiene su propia
base de datos. No se necesita un servidor compartido ni publicar un puerto.
Usar Java 17+, Maven y MySQL 8.0.16+.

## Preparación de cada equipo

Desde la raíz del proyecto, abrir el cliente MySQL como administrador:

```sh
mysql -u root -p
```

En el cliente (compatible también con Windows):

```sql
SOURCE database/schema.sql;
```

`schema.sql` crea `carmotors_test` y, al final, el usuario `carmotors_admin`
para `localhost` con sus permisos. Debe ejecutarse con un usuario administrador.
**Volver a ejecutar schema.sql borra y recrea esa base**, por lo que debe usarse
solo al preparar/reinicializar datos locales. No lo ejecuta ConexionFactory.
El script de datos no está incluido en esta tarea; cargarlo cuando se integre.

## Configuración segura (Java 17)

`ConexionFactory` lee `CARMOTORS_DB_URL`, `CARMOTORS_DB_USER` y
`CARMOTORS_DB_PASSWORD` desde el entorno. La URL y el usuario poseen valores de
prueba por defecto, pero **la contraseña no se guarda en Java**. Para ejecutarlo
localmente en Linux, antes de iniciar IntelliJ desde esa terminal o ejecutar Java:

```sh
export CARMOTORS_DB_PASSWORD='TU_CLAVE_LOCAL'
# Opcional:
# export CARMOTORS_DB_USER='carmotors_admin'
```

En IntelliJ también se configuran estas variables en Run/Debug Configurations.
El `schema.sql` del proyecto contiene una contraseña de *demostración* para la
base local; antes de compartirla o usarla realmente, reemplázala por tu propia
contraseña y nunca publiques credenciales reales. `.env` está excluido por Git.

## Uso desde la capa de servicio

```java
try (Connection conexion = ConexionFactory.getInstancia().crearConexion()) {
    // Pasar esta misma conexión a los DAO de la operación.
}
```

`ConexionFactory` es Singleton, pero cada llamada a `crearConexion()` devuelve
una conexión nueva. El servicio cierra la conexión. Para operaciones múltiples,
el servicio usa `setAutoCommit(false)`, `commit()` y `rollback()` sobre esa misma
conexión; el DAO no confirma ni cierra conexiones recibidas.

Connector/J se registra automáticamente por JDBC; no hace falta Class.forName.

## Compilar y comprobar

```sh
mvn test
mvn package
```

Las pruebas automáticas usan un driver controlado: verifican el Singleton,
conexiones independientes, cierre por el llamador y propagación de errores,
sin necesitar MySQL ni ejecutar scripts destructivos.

Para verificar el servidor real con las credenciales locales:

```sh
mysql -h localhost -P 3306 -u carmotors_admin -p carmotors_test
```

Dentro del cliente: `SELECT DATABASE(), CURRENT_USER();`.
Esto verifica MySQL, pero la comprobación JDBC real debe ejecutarse también con
`crearConexion()` cuando el servidor esté disponible. Esta tarea no añade Main
ni menús de negocio.
