CREATE TABLE mantenimiento_tipos (
    id INT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE servicios (
    id INT PRIMARY KEY,
    mantenimiento_tipo_id INT NOT NULL REFERENCES mantenimiento_tipos(id),
    fecha_recepcion DATETIME(6) NOT NULL
);

INSERT INTO mantenimiento_tipos (id, nombre) VALUES
    (1, 'Preventivo'), (2, 'Correctivo');

INSERT INTO servicios (id, mantenimiento_tipo_id, fecha_recepcion) VALUES
    (1, 1, '2026-10-31 23:59:59.999999'),
    (2, 1, '2026-11-01 00:00:00.000000'),
    (3, 2, '2026-11-01 13:00:00.000000'),
    (4, 1, '2026-11-30 23:59:59.999999'),
    (5, 2, '2026-12-01 00:00:00.000000');
