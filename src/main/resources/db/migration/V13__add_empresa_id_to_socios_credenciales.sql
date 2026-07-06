-- 1. Agregar columna empresa_id a la tabla socios_credenciales
ALTER TABLE socios_credenciales ADD COLUMN IF NOT EXISTS empresa_id INT REFERENCES empresas(id);

-- 2. Poblar la columna empresa_id con el valor correspondiente del socio asociado, pero sólo si la empresa existe
UPDATE socios_credenciales sc
SET empresa_id = s.empresa_id
FROM socios s
WHERE sc.socio_id = s.id 
  AND sc.empresa_id IS NULL
  AND s.empresa_id IN (SELECT id FROM empresas);

-- 3. Si aun existen registros huérfanos o inválidos, asignar el tenant por defecto (1)
UPDATE socios_credenciales SET empresa_id = 1 WHERE empresa_id IS NULL;

-- 4. Hacer la columna empresa_id NOT NULL
ALTER TABLE socios_credenciales ALTER COLUMN empresa_id SET NOT NULL;
