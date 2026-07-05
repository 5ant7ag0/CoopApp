-- Consolidado de Refactorización Fase 1 - Base de Datos
-- Autor: Antigravity AI
-- Fecha: 2026-07-05

-- ==========================================
-- 1. AISLAMIENTO MULTI-TENANT DE AGENCIAS Y CAJAS
-- ==========================================

-- 1.1. Modificar agencias para incluir empresa_id
ALTER TABLE agencias ADD COLUMN IF NOT EXISTS empresa_id INT REFERENCES empresas(id);

-- Actualizar registros existentes al Tenant ID = 1 (CAC ITQ)
UPDATE agencias SET empresa_id = 1 WHERE empresa_id IS NULL;
ALTER TABLE agencias ALTER COLUMN empresa_id SET NOT NULL;

-- Cambiar restricción de unicidad del código de Agencia para que sea compuesta (por empresa)
ALTER TABLE agencias DROP CONSTRAINT IF EXISTS agencias_codigo_key;
ALTER TABLE agencias DROP CONSTRAINT IF EXISTS uk_empresa_codigo_agencia;
ALTER TABLE agencias ADD CONSTRAINT uk_empresa_codigo_agencia UNIQUE (empresa_id, codigo);

-- 1.2. Modificar cajas_ventanilla para aislar código por empresa
ALTER TABLE cajas_ventanilla DROP CONSTRAINT IF EXISTS cajas_ventanilla_codigo_key;
ALTER TABLE cajas_ventanilla DROP CONSTRAINT IF EXISTS uk_empresa_codigo_caja;
ALTER TABLE cajas_ventanilla ADD CONSTRAINT uk_empresa_codigo_caja UNIQUE (empresa_id, codigo);


-- ==========================================
-- 2. AISLAMIENTO MULTI-TENANT EN TABLAS OPERATIVAS Y AUXILIARES
-- ==========================================

-- 2.1. Cuotas de Amortización (enlace compuesto con creditos)
ALTER TABLE cuotas_amortizacion ADD COLUMN IF NOT EXISTS empresa_id INT;
-- Rellenar empresa_id a partir de la tabla creditos asociada
UPDATE cuotas_amortizacion ca
SET empresa_id = c.empresa_id
FROM creditos c
WHERE ca.credito_id = c.id AND ca.empresa_id IS NULL;

-- Asignar por defecto 1 si queda algún huérfano, y hacer NOT NULL
UPDATE cuotas_amortizacion SET empresa_id = 1 WHERE empresa_id IS NULL;
ALTER TABLE cuotas_amortizacion ALTER COLUMN empresa_id SET NOT NULL;

-- Eliminar FK antigua y añadir FK compuesta
ALTER TABLE cuotas_amortizacion DROP CONSTRAINT IF EXISTS cuotas_amortizacion_credito_id_fkey;
ALTER TABLE cuotas_amortizacion DROP CONSTRAINT IF EXISTS fk_cuotas_credito_empresa;
ALTER TABLE cuotas_amortizacion 
ADD CONSTRAINT fk_cuotas_credito_empresa 
FOREIGN KEY (credito_id, empresa_id) 
REFERENCES creditos(id, empresa_id) ON DELETE RESTRICT;


-- 2.2. Socios Credenciales (enlace compuesto con socios)
ALTER TABLE socios_credenciales ADD COLUMN IF NOT EXISTS empresa_id INT;
-- Rellenar empresa_id a partir del socio
UPDATE socios_credenciales sc
SET empresa_id = s.empresa_id
FROM socios s
WHERE sc.socio_id = s.id AND sc.empresa_id IS NULL;

UPDATE socios_credenciales SET empresa_id = 1 WHERE empresa_id IS NULL;
ALTER TABLE socios_credenciales ALTER COLUMN empresa_id SET NOT NULL;

ALTER TABLE socios_credenciales DROP CONSTRAINT IF EXISTS socios_credenciales_socio_id_fkey;
ALTER TABLE socios_credenciales DROP CONSTRAINT IF EXISTS fk_credenciales_socio_empresa;
ALTER TABLE socios_credenciales 
ADD CONSTRAINT fk_credenciales_socio_empresa 
FOREIGN KEY (socio_id, empresa_id) 
REFERENCES socios(id, empresa_id) ON DELETE CASCADE;


-- 2.3. Socios Beneficiarios (enlace compuesto con socios)
ALTER TABLE socios_beneficiarios ADD COLUMN IF NOT EXISTS empresa_id INT;
UPDATE socios_beneficiarios sb
SET empresa_id = s.empresa_id
FROM socios s
WHERE sb.socio_id = s.id AND sb.empresa_id IS NULL;

UPDATE socios_beneficiarios SET empresa_id = 1 WHERE empresa_id IS NULL;
ALTER TABLE socios_beneficiarios ALTER COLUMN empresa_id SET NOT NULL;

ALTER TABLE socios_beneficiarios DROP CONSTRAINT IF EXISTS socios_beneficiarios_socio_id_fkey;
ALTER TABLE socios_beneficiarios DROP CONSTRAINT IF EXISTS fk_beneficiarios_socio_empresa;
ALTER TABLE socios_beneficiarios 
ADD CONSTRAINT fk_beneficiarios_socio_empresa 
FOREIGN KEY (socio_id, empresa_id) 
REFERENCES socios(id, empresa_id) ON DELETE CASCADE;


-- 2.4. Plan Aportaciones Mensuales (enlace compuesto con cuentas_ahorros)
ALTER TABLE plan_aportaciones_mensuales ADD COLUMN IF NOT EXISTS empresa_id INT;
UPDATE plan_aportaciones_mensuales pam
SET empresa_id = ca.empresa_id
FROM cuentas_ahorros ca
WHERE pam.cuenta_id = ca.id AND pam.empresa_id IS NULL;

UPDATE plan_aportaciones_mensuales SET empresa_id = 1 WHERE empresa_id IS NULL;
ALTER TABLE plan_aportaciones_mensuales ALTER COLUMN empresa_id SET NOT NULL;

-- Eliminar FK anterior
ALTER TABLE plan_aportaciones_mensuales DROP CONSTRAINT IF EXISTS plan_aportaciones_mensuales_cuenta_id_fkey;
ALTER TABLE plan_aportaciones_mensuales DROP CONSTRAINT IF EXISTS fk_plan_cuenta_aportaciones;
ALTER TABLE plan_aportaciones_mensuales DROP CONSTRAINT IF EXISTS fk_aportaciones_cuenta_empresa;

ALTER TABLE plan_aportaciones_mensuales 
ADD CONSTRAINT fk_aportaciones_cuenta_empresa 
FOREIGN KEY (cuenta_id, empresa_id) 
REFERENCES cuentas_ahorros(id, empresa_id) ON DELETE RESTRICT;


-- 2.5. Historial de Estados de Crédito (enlace compuesto con creditos)
ALTER TABLE historial_estados_credito ADD COLUMN IF NOT EXISTS empresa_id INT;
UPDATE historial_estados_credito hec
SET empresa_id = c.empresa_id
FROM creditos c
WHERE hec.credito_id = c.id AND hec.empresa_id IS NULL;

UPDATE historial_estados_credito SET empresa_id = 1 WHERE empresa_id IS NULL;
ALTER TABLE historial_estados_credito ALTER COLUMN empresa_id SET NOT NULL;

ALTER TABLE historial_estados_credito DROP CONSTRAINT IF EXISTS historial_estados_credito_credito_id_fkey;
ALTER TABLE historial_estados_credito DROP CONSTRAINT IF EXISTS fk_historial_credito_empresa;
ALTER TABLE historial_estados_credito 
ADD CONSTRAINT fk_historial_credito_empresa 
FOREIGN KEY (credito_id, empresa_id) 
REFERENCES creditos(id, empresa_id) ON DELETE CASCADE;


-- ==========================================
-- 3. INTEGRIDAD TRANSACCIONAL (CONTABILIDAD)
-- ==========================================

-- 3.1. Cambiar la regla de borrado en asientos_cabecera de SET NULL a RESTRICT
ALTER TABLE asientos_cabecera DROP CONSTRAINT IF EXISTS fk_cabecera_ledger_empresa;
ALTER TABLE asientos_cabecera 
ADD CONSTRAINT fk_cabecera_ledger_empresa 
FOREIGN KEY (transaccion_ledger_id, empresa_id) 
REFERENCES transacciones_ledger(id, empresa_id) ON DELETE RESTRICT;


-- 3.2. Triggers para inmutabilidad física del Ledger (Libro Diario y Mayor)
CREATE OR REPLACE FUNCTION fn_prevenir_mutacion_ledger()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Error de Integridad Contable: No se permiten modificaciones (UPDATE) ni eliminaciones (DELETE) directas en el Libro Diario o Libro Mayor (Ledger). Cualquier corrección debe realizarse mediante un asiento de reversión/contrapartida.';
END;
$$ LANGUAGE plpgsql;

-- Trigger para transacciones_ledger
DROP TRIGGER IF EXISTS trg_inmutabilidad_ledger ON transacciones_ledger;
CREATE TRIGGER trg_inmutabilidad_ledger
BEFORE UPDATE OR DELETE ON transacciones_ledger
FOR EACH ROW
EXECUTE FUNCTION fn_prevenir_mutacion_ledger();

-- Trigger para asientos_cabecera
DROP TRIGGER IF EXISTS trg_inmutabilidad_cabecera ON asientos_cabecera;
CREATE TRIGGER trg_inmutabilidad_cabecera
BEFORE UPDATE OR DELETE ON asientos_cabecera
FOR EACH ROW
EXECUTE FUNCTION fn_prevenir_mutacion_ledger();

-- Trigger para asientos_detalle
DROP TRIGGER IF EXISTS trg_inmutabilidad_detalle ON asientos_detalle;
CREATE TRIGGER trg_inmutabilidad_detalle
BEFORE UPDATE OR DELETE ON asientos_detalle
FOR EACH ROW
EXECUTE FUNCTION fn_prevenir_mutacion_ledger();


-- ==========================================
-- 4. OPTIMIZACIÓN Y RENDIMIENTO (ÍNDICES)
-- ==========================================

-- 4.1. Índice compuesto para consultas en el Libro Diario/Mayor (Estado de Cuenta, etc.)
CREATE INDEX IF NOT EXISTS idx_transacciones_ledger_cuenta_fecha 
ON transacciones_ledger(empresa_id, cuenta_id, fecha_contable);

-- 4.2. Índice compuesto para el proceso batch de liquidación de Mora y Devengos
CREATE INDEX IF NOT EXISTS idx_cuotas_amortizacion_credito_fecha 
ON cuotas_amortizacion(credito_id, estado, fecha_vencimiento);

-- 4.3. Índice para búsquedas frecuentes de créditos por socio
CREATE INDEX IF NOT EXISTS idx_creditos_socio_estado 
ON creditos(empresa_id, socio_id, estado);

-- 4.4. Índice para búsquedas frecuentes de cuentas de ahorro por socio
CREATE INDEX IF NOT EXISTS idx_cuentas_ahorros_socio_estado 
ON cuentas_ahorros(empresa_id, socio_id, estado);


-- ==========================================
-- 5. AUDITORÍA BASE (APOYO AL MODELO DRY)
-- ==========================================
-- Asegurar que todas las tablas que mapean AuditableEntity tengan 'updated_at'
ALTER TABLE agencias ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE cajas_ventanilla ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE socios ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE usuarios_admin ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE creditos ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE plan_cuentas ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE cuotas_amortizacion ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
