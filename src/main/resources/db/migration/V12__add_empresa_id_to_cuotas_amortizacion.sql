-- 1. Agregar columna empresa_id a la tabla cuotas_amortizacion
ALTER TABLE cuotas_amortizacion ADD COLUMN IF NOT EXISTS empresa_id INT REFERENCES empresas(id);

-- 2. Poblar la columna empresa_id con el valor correspondiente del credito asociado
UPDATE cuotas_amortizacion c
SET empresa_id = cr.empresa_id
FROM creditos cr
WHERE c.credito_id = cr.id AND c.empresa_id IS NULL;

-- 3. Si aun existen registros huérfanos, asignar el tenant por defecto (1)
UPDATE cuotas_amortizacion SET empresa_id = 1 WHERE empresa_id IS NULL;

-- 4. Hacer la columna empresa_id NOT NULL
ALTER TABLE cuotas_amortizacion ALTER COLUMN empresa_id SET NOT NULL;
