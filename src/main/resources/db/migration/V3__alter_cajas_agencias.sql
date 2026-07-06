-- 1. Crear la tabla de Agencias
CREATE TABLE IF NOT EXISTS agencias (
    id SERIAL PRIMARY KEY,
    codigo VARCHAR(20) UNIQUE NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    direccion VARCHAR(255),
    estado VARCHAR(20) DEFAULT 'ACTIVA',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insertar una Agencia por defecto si no existe
INSERT INTO agencias (codigo, nombre, direccion)
VALUES ('AG-001', 'Agencia Matriz', 'Av. Principal y Central')
ON CONFLICT (codigo) DO NOTHING;

-- 2. Alterar la tabla cajas_ventanilla existente
ALTER TABLE cajas_ventanilla
ADD COLUMN IF NOT EXISTS codigo VARCHAR(20) UNIQUE,
ADD COLUMN IF NOT EXISTS agencia_id INTEGER REFERENCES agencias(id),
ADD COLUMN IF NOT EXISTS saldo_base NUMERIC(19,4) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS saldo_actual NUMERIC(19,4) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS limite_efectivo_maximo NUMERIC(19,4) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS cuenta_contable_id INTEGER REFERENCES plan_cuentas(id);

-- Actualizar registros existentes si hay para evitar nulos problemáticos (Opcional, depende de la BD actual)
-- Si hay cajas, les ponemos un código temporal para que no rompa la unicidad en futuras validaciones
UPDATE cajas_ventanilla SET codigo = 'CAJA-TEMP-' || id WHERE codigo IS NULL;
UPDATE cajas_ventanilla SET saldo_base = 0.00 WHERE saldo_base IS NULL;
UPDATE cajas_ventanilla SET saldo_actual = 0.00 WHERE saldo_actual IS NULL;
UPDATE cajas_ventanilla SET limite_efectivo_maximo = 0.00 WHERE limite_efectivo_maximo IS NULL;
