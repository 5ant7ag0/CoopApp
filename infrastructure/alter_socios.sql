-- Script de migración incremental para la tabla socios
-- Añade los campos necesarios para la Vista 360 del Socio

ALTER TABLE socios ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(50);
ALTER TABLE socios ADD COLUMN IF NOT EXISTS profesion VARCHAR(100);
ALTER TABLE socios ADD COLUMN IF NOT EXISTS firma_url VARCHAR(255);
