CREATE TABLE repuestos (
    id INT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL
);

CREATE TABLE reserva_estados (
    id INT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE reservas_repuestos (
    id INT PRIMARY KEY,
    repuesto_id INT NOT NULL REFERENCES repuestos(id),
    estado_id INT NOT NULL REFERENCES reserva_estados(id),
    cantidad BIGINT NOT NULL CHECK (cantidad > 0),
    fecha_reserva DATETIME(6) NOT NULL,
    fecha_consumo DATETIME(6),
    CHECK (fecha_consumo >= fecha_reserva)
);

INSERT INTO repuestos VALUES (1, 'Filtro'), (2, 'Bujía'), (3, 'Aceite'), (4, 'Correa');
INSERT INTO reserva_estados VALUES (1, 'ACTIVA'), (2, 'CONSUMIDA'), (3, 'LIBERADA');

INSERT INTO reservas_repuestos VALUES
    (1, 1, 2, 3, '2026-10-31 08:00:00', '2026-11-01 00:00:00'),
    (2, 1, 2, 4, '2026-11-15 08:00:00', '2026-11-30 23:59:59.999999'),
    (3, 2, 2, 2, '2026-11-15 08:00:00', '2026-11-15 10:00:00'),
    (4, 3, 2, 2, '2026-11-15 08:00:00', '2026-11-15 10:00:00'),
    (5, 1, 1, 80, '2026-11-01 08:00:00', NULL),
    (6, 4, 3, 90, '2026-11-02 08:00:00', NULL),
    (7, 1, 2, 60, '2026-10-31 08:00:00', '2026-10-31 23:59:59.999999'),
    (8, 1, 2, 50, '2026-11-30 08:00:00', '2026-12-01 00:00:00'),
    (9, 2, 2, 40, '2026-11-03 08:00:00', NULL),
    (10, 1, 2, 3000000000, '2026-12-01 08:00:00', '2026-12-01 09:00:00'),
    (11, 4, 3, 100, '2026-11-02 08:00:00', '2026-11-02 10:00:00');
