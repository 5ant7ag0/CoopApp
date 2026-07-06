-- 1. Reparar la tabla usuarios_admin: renombrar ultimo_login a ultimo_acceso y agregar updated_at
ALTER TABLE usuarios_admin RENAME COLUMN ultimo_login TO ultimo_acceso;
ALTER TABLE usuarios_admin ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 2. Agregar updated_at a socios
ALTER TABLE socios ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 3. Agregar updated_at a cuentas_ahorros
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 4. Agregar updated_at a creditos
ALTER TABLE creditos ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 5. Agregar updated_at a cuotas_amortizacion
ALTER TABLE cuotas_amortizacion ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 6. Agregar updated_at a cajas_ventanilla
ALTER TABLE cajas_ventanilla ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 7. Agregar updated_at a agencias
ALTER TABLE agencias ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
