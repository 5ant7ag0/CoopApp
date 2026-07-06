-- 1. Agregar columnas faltantes en la tabla socios
ALTER TABLE socios ADD COLUMN IF NOT EXISTS estado_civil VARCHAR(50);
ALTER TABLE socios ADD COLUMN IF NOT EXISTS profesion VARCHAR(100);

-- 2. Insertar Gerente General de ITQ (Tenant ID = 1)
INSERT INTO usuarios_admin (empresa_id, username, password_hash, nombres_completos, correo, rol, estado, identificacion)
VALUES (1, 'gerente_itq', '$2a$10$.mUVoUfWa9YO757.5gNMbOpqYUIYqFvL76fHcny7vbgWA3EPcIMKq', 'Gerente ITQ', 'gerente@itq.edu.ec', 'GERENTE_GENERAL', 'ACTIVO', '1724032169')
ON CONFLICT (empresa_id, username) DO NOTHING;
