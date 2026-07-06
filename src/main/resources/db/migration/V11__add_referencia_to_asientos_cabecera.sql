-- 1. Agregar columna referencia a la tabla asientos_cabecera
ALTER TABLE asientos_cabecera ADD COLUMN IF NOT EXISTS referencia VARCHAR(100);
