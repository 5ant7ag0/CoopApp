-- 1. Crear tabla de catálogo de cajas de ventanilla
CREATE TABLE IF NOT EXISTS cajas_ventanilla (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    nombre VARCHAR(50) NOT NULL,
    estado VARCHAR(20) DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'INACTIVA')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insertar algunas cajas de prueba por defecto para la empresa 1
INSERT INTO cajas_ventanilla (empresa_id, nombre, estado) VALUES (1, 'Caja 01 - Ventanilla Principal', 'ACTIVA');
INSERT INTO cajas_ventanilla (empresa_id, nombre, estado) VALUES (1, 'Caja 02 - Ventanilla Auxiliar', 'ACTIVA');
INSERT INTO cajas_ventanilla (empresa_id, nombre, estado) VALUES (1, 'Caja 03 - Sucursal Norte', 'ACTIVA');

-- 2. Alterar tabla usuarios_admin para inyectar campos RBAC, seguridad y perfil completo
ALTER TABLE usuarios_admin
ADD COLUMN IF NOT EXISTS identificacion VARCHAR(20),
ADD COLUMN IF NOT EXISTS foto_perfil_url VARCHAR(255),
ADD COLUMN IF NOT EXISTS telefono VARCHAR(20),
ADD COLUMN IF NOT EXISTS direccion VARCHAR(255),
ADD COLUMN IF NOT EXISTS cambiar_password_proximo_inicio BOOLEAN DEFAULT TRUE,
ADD COLUMN IF NOT EXISTS caja_id INT REFERENCES cajas_ventanilla(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS limite_transaccion_max NUMERIC(15,2) DEFAULT 0.00;

-- Llenar cédula por defecto para evitar violación de nulos en registros previos (usamos cédulas válidas)
UPDATE usuarios_admin SET identificacion = '1724032128' WHERE identificacion IS NULL;

-- Restricciones obligatorias
ALTER TABLE usuarios_admin ALTER COLUMN identificacion SET NOT NULL;
ALTER TABLE usuarios_admin ADD CONSTRAINT uk_usuarios_admin_identificacion UNIQUE (identificacion);
