-- V1__schema_inicial.sql
-- Flyway ejecuta este archivo en orden. No modificar si ya fue aplicado; crear V2__ para cambios.

CREATE TABLE IF NOT EXISTS usuarios (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(30) NOT NULL DEFAULT 'ROLE_DUENO',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios (email);

ALTER TABLE usuarios
    ADD CONSTRAINT chk_usuarios_rol
    CHECK (rol IN ('ROLE_ADMIN','ROLE_VETERINARIO','ROLE_DUENO','ROLE_AUXILIAR'));

CREATE TABLE IF NOT EXISTS mascotas (
    id BIGSERIAL PRIMARY KEY,
    duenio_id BIGINT NOT NULL,
    nombre VARCHAR(50) NOT NULL,
    especie VARCHAR(30) NOT NULL,
    raza VARCHAR(50) NOT NULL,
    fecha_nacimiento DATE NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_mascotas_duenio FOREIGN KEY (duenio_id) REFERENCES usuarios(id)
);

CREATE INDEX IF NOT EXISTS idx_mascotas_duenio ON mascotas (duenio_id);
CREATE INDEX IF NOT EXISTS idx_mascotas_activo ON mascotas (activo);

CREATE OR REPLACE FUNCTION set_actualizado_en()
RETURNS TRIGGER AS $$
BEGIN
    NEW.actualizado_en = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_usuarios_actualizado_en ON usuarios;
CREATE TRIGGER trg_usuarios_actualizado_en
BEFORE UPDATE ON usuarios
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();

DROP TRIGGER IF EXISTS trg_mascotas_actualizado_en ON mascotas;
CREATE TRIGGER trg_mascotas_actualizado_en
BEFORE UPDATE ON mascotas
FOR EACH ROW EXECUTE FUNCTION set_actualizado_en();
