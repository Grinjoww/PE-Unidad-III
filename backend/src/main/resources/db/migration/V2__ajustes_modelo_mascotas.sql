-- V2__ajustes_modelo_mascotas.sql
-- Indices funcionales para soportar filtros case-insensitive por especie y raza
-- en el listado paginado de mascotas (GET /api/mascotas?especie=...&raza=...).

CREATE INDEX IF NOT EXISTS idx_mascotas_especie_lower ON mascotas (LOWER(especie));
CREATE INDEX IF NOT EXISTS idx_mascotas_raza_lower ON mascotas (LOWER(raza));
