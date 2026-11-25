CREATE TABLE tecnicos (
    id INT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL
);

CREATE TABLE servicio_estados (
    id INT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE servicios (
    id INT PRIMARY KEY,
    estado_id INT NOT NULL REFERENCES servicio_estados(id),
    fecha_recepcion DATETIME(6) NOT NULL,
    fecha_completado DATETIME(6)
);

CREATE TABLE servicios_tecnicos (
    servicio_id INT NOT NULL REFERENCES servicios(id),
    tecnico_id INT NOT NULL REFERENCES tecnicos(id),
    PRIMARY KEY (servicio_id, tecnico_id)
);

INSERT INTO tecnicos VALUES (1, 'Ana'), (2, 'Ana'), (3, 'Luis');
INSERT INTO servicio_estados VALUES
    (1, 'Pendiente'), (2, 'En Proceso'), (3, 'Completado'), (4, 'Facturado'), (5, 'Entregado');

INSERT INTO servicios VALUES
    (1, 3, '2026-10-30 09:00:00', '2026-11-01 00:00:00'),
    (2, 4, '2026-11-15 09:00:00', '2026-11-15 18:00:00'),
    (3, 5, '2026-11-28 09:00:00', '2026-11-30 23:59:59.999999'),
    (4, 2, '2026-11-01 09:00:00', NULL),
    (5, 3, '2026-11-01 09:00:00', '2026-12-01 00:00:00'),
    (6, 3, '2026-10-30 09:00:00', '2026-10-31 23:59:59.999999'),
    (7, 1, '2026-11-01 09:00:00', '2026-11-02 18:00:00'),
    (8, 3, '2026-11-23 09:00:00', '2026-11-23 18:00:00');

INSERT INTO servicios_tecnicos VALUES
    (1, 1), (1, 2), (2, 1), (2, 3), (3, 3), (4, 2), (5, 2), (6, 2), (7, 2);
