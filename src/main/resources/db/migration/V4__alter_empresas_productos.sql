-- Script de Sincronización Estructural: Catálogo de Productos e Inquilino (SaaS)
-- Autor: Antigravity AI
-- Fecha: 2026-07-05

-- 1. Crear Tabla de Productos de Ahorro
CREATE TABLE IF NOT EXISTS productos_ahorro (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    tipo_producto VARCHAR(50) NOT NULL, -- 'AHORRO_VISTA', 'AHORRO_PROGRAMADO', 'PLAZO_FIJO', 'APORTACIONES'
    tasa_interes_anual NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    monto_minimo_apertura NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    saldo_minimo_requerido NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tipo_retiro VARCHAR(20) NOT NULL DEFAULT 'LIBRE', -- 'LIBRE', 'PENALIZADO', 'RESTRINGIDO'
    tasa_penalizacion_retiro NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    cuenta_contable_pasivo_id INT NOT NULL,
    cuenta_contable_gasto_id INT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_productos_ahorro_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT fk_prod_ahorro_pasivo FOREIGN KEY (cuenta_contable_pasivo_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_ahorro_gasto FOREIGN KEY (cuenta_contable_gasto_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT
);

-- 2. Crear Tabla de Productos de Crédito
CREATE TABLE IF NOT EXISTS productos_credito (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id) ON DELETE CASCADE,
    nombre VARCHAR(100) NOT NULL,
    monto_minimo NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    monto_maximo NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    plazo_minimo_meses INT NOT NULL,
    plazo_maximo_meses INT NOT NULL,
    tasa_interes_anual NUMERIC(5,2) NOT NULL,
    tasa_mora_anual NUMERIC(5,2) NOT NULL,
    porcentaje_seguro_desgravamen NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    cuenta_contable_cartera_id INT NOT NULL,
    cuenta_contable_ingresos_intereses_id INT NOT NULL,
    cuenta_contable_mora_id INT NOT NULL,
    cuenta_contable_seguro_id INT,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_productos_credito_id_empresa UNIQUE (id, empresa_id),
    CONSTRAINT fk_prod_credito_cartera FOREIGN KEY (cuenta_contable_cartera_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_credito_ingresos FOREIGN KEY (cuenta_contable_ingresos_intereses_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_credito_mora FOREIGN KEY (cuenta_contable_mora_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT,
    CONSTRAINT fk_prod_credito_seguro FOREIGN KEY (cuenta_contable_seguro_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT
);

-- 3. Inyectar Campos Faltantes a la tabla de Inquilinos (empresas)
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS limite_usuarios_admin INT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS limite_socios INT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS direccion VARCHAR(255);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS telefono VARCHAR(15);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS siglas VARCHAR(30);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS segmento_seps VARCHAR(20);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS resolucion_seps VARCHAR(100);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS correo_institucional VARCHAR(100);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS correo_gerente VARCHAR(100);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS provincia VARCHAR(100);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS canton VARCHAR(100);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS saldo_minimo_apertura NUMERIC(15,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS monto_minimo_credito NUMERIC(15,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS monto_maximo_credito NUMERIC(15,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS tasa_interes_anual NUMERIC(5,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS tasa_interes_mora NUMERIC(5,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS costo_tramite NUMERIC(15,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS porcentaje_seguro_desgravamen NUMERIC(5,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuota_aportacion_mensual NUMERIC(15,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS tasa_interes_pasiva NUMERIC(5,2);
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS dias_gracia_mora INT;

-- Enlaces contables
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_cartera_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_seguro_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_papeleria_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_caja_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_obligaciones_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_gastos_intereses_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_ingresos_intereses_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_mora_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;
ALTER TABLE empresas ADD COLUMN IF NOT EXISTS cuenta_contable_aportaciones_id INT REFERENCES plan_cuentas(id) ON DELETE RESTRICT;

-- 4. Modificar Tabla cuentas_ahorros para enlazar con productos_ahorro
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS producto_ahorro_id INT;
ALTER TABLE cuentas_ahorros DROP CONSTRAINT IF EXISTS fk_cuentas_ahorros_producto;
ALTER TABLE cuentas_ahorros 
ADD CONSTRAINT fk_cuentas_ahorros_producto 
FOREIGN KEY (producto_ahorro_id, empresa_id) 
REFERENCES productos_ahorro(id, empresa_id) ON DELETE RESTRICT;

-- 5. Modificar Tabla creditos para enlazar con productos_credito
ALTER TABLE creditos ADD COLUMN IF NOT EXISTS producto_credito_id INT;
ALTER TABLE creditos DROP CONSTRAINT IF EXISTS fk_creditos_producto;
ALTER TABLE creditos 
ADD CONSTRAINT fk_creditos_producto 
FOREIGN KEY (producto_credito_id, empresa_id) 
REFERENCES productos_credito(id, empresa_id) ON DELETE RESTRICT;

ALTER TABLE creditos ADD COLUMN IF NOT EXISTS motivo_rechazo TEXT;
