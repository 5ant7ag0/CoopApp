-- 1. Alterar la columna socio_id para que permita valores nulos
ALTER TABLE tokens_recuperacion ALTER COLUMN socio_id DROP NOT NULL;

-- 2. Agregar la columna usuario_admin_id con llave foránea a usuarios_admin
ALTER TABLE tokens_recuperacion ADD COLUMN IF NOT EXISTS usuario_admin_id INT REFERENCES usuarios_admin(id) ON DELETE CASCADE;
