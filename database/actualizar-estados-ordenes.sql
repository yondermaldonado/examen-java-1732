-- Ajuste no destructivo para equipos que ya cargaron la versión anterior de data.sql.
-- Ejecutar en MySQL: SOURCE database/actualizar-estados-ordenes.sql;
USE carmotors_test;
START TRANSACTION;
INSERT INTO servicio_estados (nombre) VALUES
  ('Pendiente'), ('En Proceso'), ('Completado'), ('Facturado'), ('Entregado')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);
UPDATE servicios s
JOIN servicio_estados anterior ON anterior.id = s.estado_id
JOIN servicio_estados nuevo ON nuevo.nombre = CASE anterior.nombre
    WHEN 'Recibido' THEN 'Pendiente'
    WHEN 'En diagnóstico' THEN 'En Proceso'
    WHEN 'En reparación' THEN 'En Proceso'
    END
SET s.estado_id = nuevo.id
WHERE anterior.nombre IN ('Recibido', 'En diagnóstico', 'En reparación');
DELETE FROM servicio_estados WHERE nombre IN ('Recibido', 'En diagnóstico', 'En reparación');
INSERT INTO mantenimiento_tipos (nombre) VALUES ('Preventivo'), ('Correctivo')
ON DUPLICATE KEY UPDATE nombre = VALUES(nombre);
COMMIT;
