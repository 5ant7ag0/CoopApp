-- 1. Agregar columna empresa_id a agencias
ALTER TABLE agencias ADD COLUMN IF NOT EXISTS empresa_id INT REFERENCES empresas(id);

-- 2. Poblar empresa_id con 1 para registros existentes
UPDATE agencias SET empresa_id = 1 WHERE empresa_id IS NULL;

-- 3. Hacer la columna empresa_id NOT NULL
ALTER TABLE agencias ALTER COLUMN empresa_id SET NOT NULL;

-- 4. Eliminar el constraint de codigo único global y reemplazarlo por unico por empresa
ALTER TABLE agencias DROP CONSTRAINT IF EXISTS agencias_codigo_key;
ALTER TABLE agencias DROP CONSTRAINT IF EXISTS uk_empresa_codigo_agencia;
ALTER TABLE agencias ADD CONSTRAINT uk_empresa_codigo_agencia UNIQUE (empresa_id, codigo);
