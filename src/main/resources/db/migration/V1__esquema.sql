-- PrestaLab: esquema inicial (PostgreSQL 16).
-- El esquema lo crea SOLO Flyway; Hibernate se limita a validarlo (ddl-auto: validate).

CREATE TABLE usuarios (
    id             BIGSERIAL    PRIMARY KEY,
    nombre         VARCHAR(100) NOT NULL,
    correo         VARCHAR(150) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    rol            VARCHAR(20)  NOT NULL
                   CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ESTUDIANTE', 'ADMIN')),
    activo         BOOLEAN      NOT NULL,
    fecha_registro TIMESTAMP    NOT NULL
);

-- Correo único sin importar mayúsculas.
CREATE UNIQUE INDEX ux_usuarios_correo ON usuarios (LOWER(correo));

CREATE TABLE equipos (
    id          BIGSERIAL    PRIMARY KEY,
    codigo      VARCHAR(30)  NOT NULL
                CONSTRAINT uk_equipos_codigo UNIQUE,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(500),
    categoria   VARCHAR(20)  NOT NULL
                CONSTRAINT ck_equipos_categoria
                CHECK (categoria IN ('PORTATIL', 'CAMARA', 'PROYECTOR', 'KIT_ELECTRONICO', 'OTRO')),
    estado      VARCHAR(20)  NOT NULL
                CONSTRAINT ck_equipos_estado
                CHECK (estado IN ('DISPONIBLE', 'PRESTADO', 'EN_MANTENIMIENTO')),
    activo      BOOLEAN      NOT NULL
);

CREATE TABLE prestamos (
    id                        BIGSERIAL    PRIMARY KEY,
    usuario_id                BIGINT       NOT NULL
                              CONSTRAINT fk_prestamos_usuario REFERENCES usuarios (id),
    -- Sin ON DELETE CASCADE: un equipo con historial nunca se elimina (RN07).
    equipo_id                 BIGINT       NOT NULL
                              CONSTRAINT fk_prestamos_equipo REFERENCES equipos (id),
    fecha_solicitud           DATE         NOT NULL,
    fecha_devolucion_esperada DATE         NOT NULL,
    fecha_aprobacion          TIMESTAMP,
    fecha_devolucion_real     DATE,
    estado                    VARCHAR(20)  NOT NULL
                              CONSTRAINT ck_prestamos_estado
                              CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO', 'DEVUELTO', 'VENCIDO')),
    motivo_rechazo            VARCHAR(500),
    -- RN02: máximo 7 días desde la solicitud.
    CONSTRAINT ck_prestamos_duracion
        CHECK (fecha_devolucion_esperada BETWEEN fecha_solicitud AND fecha_solicitud + 7),
    CONSTRAINT ck_prestamos_motivo_rechazo
        CHECK (estado <> 'RECHAZADO' OR motivo_rechazo IS NOT NULL)
);

-- Un equipo no puede estar en dos préstamos en curso a la vez (protege aprobaciones simultáneas).
CREATE UNIQUE INDEX ux_prestamos_equipo_en_curso
    ON prestamos (equipo_id)
    WHERE estado IN ('APROBADO', 'VENCIDO');

CREATE INDEX ix_prestamos_usuario_estado ON prestamos (usuario_id, estado);
CREATE INDEX ix_prestamos_estado_fecha   ON prestamos (estado, fecha_devolucion_esperada);
