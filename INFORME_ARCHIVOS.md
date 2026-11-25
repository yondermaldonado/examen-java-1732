# Procedencia y estado de verificación

Se inspeccionó el archivo aportado por el estudiante y se comprobó por GitHub la
existencia de `66922e4ab46eae2cd9921995ce34c2902d6ef821` en
`RickDslayer/proyecto_java_grupo01`.

Comparación SHA-1 de objetos Git: **172 de 203 archivos** del árbol de ese
commit se encontraron por contenido en el ZIP original. **31 no**; entre las
principales discrepancias están `Main.java`, `OrdenServicioService.java`,
`VistaOrdenes.java`, `VistaFacturacion.java`, `VistaOperaciones.java`,
`OperacionOrdenService.java`, `FacturaController.java`, los DAO de operaciones,
los objetos de facturación, README y archivos gráficos.

Este ZIP conserva el proyecto del estudiante y las mejoras del examen 1732;
**no es una reconstrucción exacta del commit indicado**. Para una entrega en la
que sea imprescindible partir literalmente de ese commit, conviene exportar ese
SHA directamente desde GitHub y aplicar allí los archivos del patrón.

### Controles realizados
- Compilación manual de `src/main/java` con `javac --release 17`.
- Prueba `PruebaObserver1732` sin MySQL ni JUnit.
- Se preservan `pom.xml`, `database/schema.sql`, `database/data.sql` y estructura MVC.
- No se ha ejecutado una prueba contra MySQL real ni creado commit remoto.
- `ConexionFactory` ya no contiene una contraseña incrustada en Java.
