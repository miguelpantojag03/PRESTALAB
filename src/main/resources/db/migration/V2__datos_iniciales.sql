-- PrestaLab: datos iniciales de desarrollo.
-- Administrador: admin@prestalab.local / Admin123*  (hash BCrypt real, costo 12).
-- CAMBIA ESTA CONTRASEÑA ANTES DE IR A PRODUCCIÓN.
INSERT INTO usuarios (nombre, correo, password_hash, rol, activo, fecha_registro)
VALUES ('Administrador del laboratorio', 'admin@prestalab.local',
        '$2a$12$UdOS62Kdao9j9FClxUPifO8NSImuyXBjdm3pmB288ZKfq0MwP8eCu',
        'ADMIN', TRUE, CURRENT_TIMESTAMP);

-- Equipos de ejemplo: las 5 categorías y los estados DISPONIBLE y EN_MANTENIMIENTO.
-- No se siembra ningún equipo PRESTADO: sin un préstamo asociado nunca podría volver a DISPONIBLE.
INSERT INTO equipos (codigo, nombre, descripcion, categoria, estado, activo) VALUES
    ('PORT-001', 'Portátil Lenovo ThinkPad E14', 'Intel Core i5, 16 GB RAM, 512 GB SSD', 'PORTATIL', 'DISPONIBLE', TRUE),
    ('PORT-002', 'Portátil Dell Latitude 5440', 'Intel Core i7, 16 GB RAM, 512 GB SSD', 'PORTATIL', 'EN_MANTENIMIENTO', TRUE),
    ('CAM-001', 'Cámara Canon EOS R50', 'Mirrorless de 24 MP con lente 18-45 mm', 'CAMARA', 'DISPONIBLE', TRUE),
    ('CAM-002', 'Cámara Sony ZV-E10', 'Mirrorless para video 4K con lente 16-50 mm', 'CAMARA', 'DISPONIBLE', TRUE),
    ('PROY-001', 'Proyector Epson PowerLite E20', '3400 lúmenes, entrada HDMI', 'PROYECTOR', 'DISPONIBLE', TRUE),
    ('PROY-002', 'Proyector BenQ MW560', '4000 lúmenes, resolución WXGA', 'PROYECTOR', 'EN_MANTENIMIENTO', TRUE),
    ('KIT-001', 'Kit Arduino Uno R3', 'Placa, protoboard, sensores y cables', 'KIT_ELECTRONICO', 'DISPONIBLE', TRUE),
    ('KIT-002', 'Kit Raspberry Pi 5', 'Placa de 8 GB con fuente y carcasa', 'KIT_ELECTRONICO', 'DISPONIBLE', TRUE),
    ('OTRO-001', 'Trípode Manfrotto Compact', 'Trípode de aluminio de 1,6 m', 'OTRO', 'DISPONIBLE', TRUE);
