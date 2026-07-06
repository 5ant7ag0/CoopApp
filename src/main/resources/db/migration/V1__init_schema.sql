-- =============================================================================
-- SCRIPT DE REINGENIERÍA DE BASE DE DATOS: CORE-COOPERATIVA (NORMATIVA ECUATORIANA)
-- =============================================================================
-- Este script corrige el orden de creación de tablas, establece restricciones únicas
-- compuestas para garantizar el aislamiento multi-tenant y añade validación completa
-- de identificaciones (cédula, RUC natural, RUC jurídico y RUC público).
-- =============================================================================

-- Limpieza previa en caso de reinstalación
DROP TABLE IF EXISTS control_sesiones CASCADE;
DROP TABLE IF EXISTS logs_auditoria CASCADE;
DROP TABLE IF EXISTS historial_estados_credito CASCADE;
DROP TABLE IF EXISTS cuotas_amortizacion CASCADE;
DROP TABLE IF EXISTS creditos CASCADE;
DROP TABLE IF EXISTS asientos_detalle CASCADE;
DROP TABLE IF EXISTS asientos_cabecera CASCADE;
DROP TABLE IF EXISTS plan_cuentas CASCADE;
DROP TABLE IF EXISTS depositos_plazo_fijo CASCADE;
DROP TABLE IF EXISTS transacciones_ledger CASCADE;
DROP TABLE IF EXISTS plan_aportaciones_mensuales CASCADE;
DROP TABLE IF EXISTS cuentas_ahorros CASCADE;
DROP TABLE IF EXISTS socios_credenciales CASCADE;
DROP TABLE IF EXISTS socios_beneficiarios CASCADE;
DROP TABLE IF EXISTS socios CASCADE;
DROP TABLE IF EXISTS usuarios_admin CASCADE;
DROP TABLE IF EXISTS empresas CASCADE;
DROP TABLE IF EXISTS cierres_anuales CASCADE;
DROP TABLE IF EXISTS distribucion_excedentes_socios CASCADE;

DROP TYPE IF EXISTS estado_cuota CASCADE;
DROP TYPE IF EXISTS estado_credito CASCADE;
DROP TYPE IF EXISTS sistema_amortizacion CASCADE;
DROP TYPE IF EXISTS estado_dpf CASCADE;
DROP TYPE IF EXISTS tipo_movimiento CASCADE;
DROP TYPE IF EXISTS tipo_aportacion CASCADE;
DROP TYPE IF EXISTS tipo_cuenta_ahorro CASCADE;

-- 1. FUNCIONES DE VALIDACIÓN (NORMATIVA ECUATORIANA - SRI / SEPS)

-- Función para validar Cédula Ecuatoriana (Algoritmo Módulo 10)
CREATE OR REPLACE FUNCTION fn_validar_cedula(p_cedula VARCHAR) 
RETURNS BOOLEAN AS $$
DECLARE
    l_suma INT := 0;
    l_digito INT;
    l_multiplicador INT;
    l_verificador INT;
    l_provincia INT;
BEGIN
    -- Verificar longitud básica
    IF length(p_cedula) != 10 THEN
        RETURN FALSE;
    END IF;

    -- Verificar que sean solo números
    IF p_cedula !~ '^[0-9]+$' THEN
        RETURN FALSE;
    END IF;

    -- Validar código de provincia (01 a 24, o 30)
    l_provincia := substring(p_cedula FROM 1 FOR 2)::INT;
    IF NOT (l_provincia BETWEEN 1 AND 24 OR l_provincia = 30) THEN
        RETURN FALSE;
    END IF;

    -- Validar el tercer dígito (debe ser menor a 6 para personas naturales)
    IF (substring(p_cedula FROM 3 FOR 1)::INT) >= 6 THEN
        RETURN FALSE;
    END IF;

    -- Algoritmo 2-1-2-1...
    FOR i IN 1..9 LOOP
        l_digito := substring(p_cedula FROM i FOR 1)::INT;
        IF i % 2 = 0 THEN
            l_multiplicador := 1;
        ELSE
            l_multiplicador := 2;
        END IF;
        
        l_digito := l_digito * l_multiplicador;
        IF l_digito > 9 THEN
            l_digito := l_digito - 9;
        END IF;
        l_suma := l_suma + l_digito;
    END LOOP;

    l_verificador := substring(p_cedula FROM 10 FOR 1)::INT;
    
    IF l_suma % 10 = 0 THEN
        RETURN l_verificador = 0;
    ELSE
        RETURN l_verificador = (10 - (l_suma % 10));
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Función para validar RUC de Personas Jurídicas Privadas (Módulo 11, tercer dígito = 9)
CREATE OR REPLACE FUNCTION fn_validar_ruc_juridico(p_ruc VARCHAR)
RETURNS BOOLEAN AS $$
DECLARE
    l_suma INT := 0;
    l_coeficientes INT[] := ARRAY[4, 3, 2, 7, 6, 5, 4, 3, 2];
    l_verificador INT;
    l_residuo INT;
    l_digito_calculado INT;
BEGIN
    IF length(p_ruc) != 13 OR substring(p_ruc FROM 11 FOR 3) != '001' THEN
        RETURN FALSE;
    END IF;

    -- Multiplicar los primeros 9 dígitos por sus coeficientes
    FOR i IN 1..9 LOOP
        l_suma := l_suma + (substring(p_ruc FROM i FOR 1)::INT * l_coeficientes[i]);
    END LOOP;

    l_verificador := substring(p_ruc FROM 10 FOR 1)::INT;
    l_residuo := l_suma % 11;

    IF l_residuo = 0 THEN
        l_digito_calculado := 0;
    ELSE
        l_digito_calculado := 11 - l_residuo;
    END IF;

    RETURN l_verificador = l_digito_calculado;
END;
$$ LANGUAGE plpgsql;

-- Función para validar RUC de Entidades Públicas (Módulo 11, tercer dígito = 6)
CREATE OR REPLACE FUNCTION fn_validar_ruc_publico(p_ruc VARCHAR)
RETURNS BOOLEAN AS $$
DECLARE
    l_suma INT := 0;
    l_coeficientes INT[] := ARRAY[3, 2, 7, 6, 5, 4, 3, 2];
    l_verificador INT;
    l_residuo INT;
    l_digito_calculado INT;
BEGIN
    IF length(p_ruc) != 13 OR substring(p_ruc FROM 10 FOR 4) != '0001' THEN
        RETURN FALSE;
    END IF;

    -- Multiplicar los primeros 8 dígitos por sus coeficientes
    FOR i IN 1..8 LOOP
        l_suma := l_suma + (substring(p_ruc FROM i FOR 1)::INT * l_coeficientes[i]);
    END LOOP;

    l_verificador := substring(p_ruc FROM 9 FOR 1)::INT;
    l_residuo := l_suma % 11;

    IF l_residuo = 0 THEN
        l_digito_calculado := 0;
    ELSE
        l_digito_calculado := 11 - l_residuo;
    END IF;

    RETURN l_verificador = l_digito_calculado;
END;
$$ LANGUAGE plpgsql;

-- Función de Validación Global de Identificación
CREATE OR REPLACE FUNCTION fn_validar_identificacion(p_tipo VARCHAR, p_identificacion VARCHAR)
RETURNS BOOLEAN AS $$
DECLARE
    l_tercer_digito INT;
BEGIN
    CASE p_tipo
        WHEN 'C' THEN -- Cédula
            RETURN fn_validar_cedula(p_identificacion);
        WHEN 'R' THEN -- RUC (Personas Naturales, Jurídicas o Públicas)
            IF length(p_identificacion) != 13 THEN
                RETURN FALSE;
            END IF;
            
            l_tercer_digito := substring(p_identificacion FROM 3 FOR 1)::INT;
            
            IF l_tercer_digito < 6 THEN
                -- Persona Natural: primeros 10 dígitos deben ser una cédula válida y terminar en 001
                IF substring(p_identificacion FROM 11 FOR 3) != '001' THEN
                    RETURN FALSE;
                END IF;
                RETURN fn_validar_cedula(substring(p_identificacion FROM 1 FOR 10));
            ELSIF l_tercer_digito = 9 THEN
                -- Persona Jurídica Privada o Extranjera
                RETURN fn_validar_ruc_juridico(p_identificacion);
            ELSIF l_tercer_digito = 6 THEN
                -- Entidad del Estado / Pública
                RETURN fn_validar_ruc_publico(p_identificacion);
            ELSE
                RETURN FALSE;
            END IF;
        WHEN 'P' THEN -- Pasaporte (Alfanumérico estándar de 3 a 15 caracteres)
            RETURN p_identificacion ~ '^[A-Z0-9]{3,15}$';
        ELSE
            RETURN FALSE;
    END CASE;
END;
$$ LANGUAGE plpgsql;


-- 2. TABLAS TRANSVERSALES DE PARAMETRIZACIÓN E INQUILINOS (TENANTS)

CREATE TABLE empresas (
    id SERIAL PRIMARY KEY,
    ruc VARCHAR(13) NOT NULL UNIQUE,
    razon_social VARCHAR(150) NOT NULL,
    nombre_comercial VARCHAR(150) NOT NULL,
    codigo_seps VARCHAR(50) NOT NULL UNIQUE,
    representante_legal VARCHAR(100) NOT NULL,
    cedula_representante VARCHAR(10) NOT NULL,
    logo_url VARCHAR(255),
    moneda VARCHAR(3) DEFAULT 'USD',
    estado VARCHAR(20) DEFAULT 'ACTIVO' CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'INACTIVO')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE usuarios_admin (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nombres_completos VARCHAR(150) NOT NULL, -- Ampliado a 150 para coincidir con la entidad Java
    correo VARCHAR(100) NOT NULL,
    rol VARCHAR(30) NOT NULL CHECK (rol IN ('SUPER_ADMIN_SAAS', 'GERENTE_GENERAL', 'OFICIAL_DE_CREDITO', 'CAJERO', 'AUDITOR_INTERNO', 'CONTADOR')),
    estado VARCHAR(20) DEFAULT 'ACTIVO',
    ultimo_login TIMESTAMP,
    ultima_ip VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_empresa_username UNIQUE (empresa_id, username)
);

-- Plan de Cuentas Contable: Reubicado antes de las tablas de asientos
CREATE TABLE plan_cuentas (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id) ON DELETE RESTRICT,
    codigo_contable VARCHAR(30) NOT NULL,
    nombre_cuenta VARCHAR(150) NOT NULL,
    tipo_cuenta VARCHAR(20) NOT NULL CHECK (tipo_cuenta IN ('ACTIVO', 'PASIVO', 'PATRIMONIO', 'INGRESO', 'GASTO')),
    es_movimiento BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Restricciones de integridad referencial compuesta para aislamiento multi-tenant
    CONSTRAINT uk_empresa_codigo UNIQUE (empresa_id, codigo_contable),
    CONSTRAINT uk_plan_cuentas_empresa_id UNIQUE (id, empresa_id) -- Permite referencias compuestas
);

CREATE INDEX idx_plan_cuentas_codigo ON plan_cuentas(empresa_id, codigo_contable);


-- 3. MÓDULO CORE: SOCIOS (CON FÓRMULA AUTOMÁTICA Y SOPORTE MULTI-TENANT)

CREATE TABLE socios (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    tipo_identificacion CHAR(1) NOT NULL CHECK (tipo_identificacion IN ('C', 'R', 'P')),
    identificacion VARCHAR(15) NOT NULL,
    nombres_completos VARCHAR(150) NOT NULL,
    direccion VARCHAR(255) NOT NULL,
    telefono VARCHAR(15) NOT NULL CHECK (telefono ~ '^09[0-9]{8}$'),
    correo VARCHAR(100) NOT NULL CHECK (correo ~* '^[A-Za-z0-9._%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,4}$'),
    actividad_economica VARCHAR(100) NOT NULL,
    lugar_trabajo VARCHAR(150),
    
    ingresos_mensuales NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    gastos_mensuales NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    deudas_actuales NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    
    capacidad_pago NUMERIC(15,2) GENERATED ALWAYS AS (ingresos_mensuales - gastos_mensuales - deudas_actuales) STORED,
    
    foto_perfil_url VARCHAR(255),
    foto_cedula_frontal_url VARCHAR(255),
    foto_cedula_posterior_url VARCHAR(255),
    firma_url VARCHAR(255),
    
    es_pep BOOLEAN DEFAULT FALSE,
    email_verified_at TIMESTAMP,
    phone_verified_at TIMESTAMP,
    estado VARCHAR(20) DEFAULT 'PENDIENTE_APROBACION' CHECK (estado IN ('PENDIENTE_APROBACION', 'ACTIVO', 'INACTIVO', 'SUSPENDIDO')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_empresa_identificacion UNIQUE (empresa_id, identificacion),
    CONSTRAINT uk_socio_empresa UNIQUE (id, empresa_id), -- Habilita integridad compuesta
    CONSTRAINT chk_identificacion_valida CHECK (fn_validar_identificacion(tipo_identificacion, identificacion) = TRUE)
);

CREATE TABLE socios_beneficiarios (
    id SERIAL PRIMARY KEY,
    socio_id INT NOT NULL REFERENCES socios(id) ON DELETE CASCADE,
    nombres_completos VARCHAR(150) NOT NULL,
    identificacion VARCHAR(15) NOT NULL,
    parentesco VARCHAR(50) NOT NULL,
    porcentaje_asignado NUMERIC(5,2) NOT NULL CHECK (porcentaje_asignado > 0 AND porcentaje_asignado <= 100)
);

CREATE TABLE socios_credenciales (
    id SERIAL PRIMARY KEY,
    socio_id INT NOT NULL REFERENCES socios(id) ON DELETE CASCADE UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    token_mfa_secreto VARCHAR(100),
    dispositivo_huella_id VARCHAR(255),
    estado_acceso VARCHAR(20) DEFAULT 'ACTIVO',
    intentos_fallidos INT DEFAULT 0,
    bloqueado_hasta TIMESTAMP
);


-- 4. TIPOS ENUMS Y CUENTAS DE AHORRO / APORTACIONES

CREATE TYPE tipo_cuenta_ahorro AS ENUM ('AHORRO_VISTA', 'APORTACIONES', 'AHORRO_PROGRAMADO', 'PLAZO_FIJO');
CREATE TYPE tipo_aportacion AS ENUM ('OBLIGATORIA', 'VOLUNTARIA', 'EXTRAORDINARIA');
CREATE TYPE tipo_movimiento AS ENUM ('DEBITO', 'CREDITO');
CREATE TYPE estado_dpf AS ENUM ('CONSTITUIDO', 'RETENIDO', 'LIQUIDADO', 'ANULADO');

CREATE TABLE cuentas_ahorros (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    socio_id INT NOT NULL,
    numero_cuenta VARCHAR(20) NOT NULL,
    tipo tipo_cuenta_ahorro NOT NULL,
    saldo NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (saldo >= 0),
    tasa_interes_anual NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    interes_acumulado NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(20) DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'INACTIVA', 'BLOQUEADA')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Seguridad SaaS: Clave foránea compuesta. Evita crear una cuenta para un socio de otra cooperativa
    CONSTRAINT fk_cuenta_socio_empresa FOREIGN KEY (socio_id, empresa_id) REFERENCES socios(id, empresa_id),
    CONSTRAINT uk_empresa_socio_tipo UNIQUE (empresa_id, socio_id, tipo),
    -- Claves de unicidad para referencias compuestas en tablas hijas
    CONSTRAINT uk_cuenta_empresa UNIQUE (id, empresa_id),
    CONSTRAINT uk_cuenta_tipo UNIQUE (id, tipo),
    CONSTRAINT uk_empresa_numero_cuenta UNIQUE (empresa_id, numero_cuenta) -- Corregido: Unicidad por Cooperativa
);

-- Detalle específico para el control de Aportaciones Obligatorias
CREATE TABLE plan_aportaciones_mensuales (
    id SERIAL PRIMARY KEY,
    cuenta_id INT NOT NULL,
    periodo_fiscal VARCHAR(7) NOT NULL, -- 'YYYY-MM'
    monto_obligatorio NUMERIC(15,2) NOT NULL CHECK (monto_obligatorio > 0),
    monto_pagado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (monto_pagado >= 0),
    fecha_maxima_pago DATE NOT NULL,
    estado_pago VARCHAR(20) DEFAULT 'PENDIENTE' CHECK (estado_pago IN ('PENDIENTE', 'PAGADO', 'EN_MORA')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Propagación de tipo: fuerza a nivel de BD que el plan mensual sea solo para cuentas de APORTACIONES
    tipo_cuenta tipo_cuenta_ahorro DEFAULT 'APORTACIONES' CHECK (tipo_cuenta = 'APORTACIONES'),
    CONSTRAINT fk_plan_cuenta_aportaciones FOREIGN KEY (cuenta_id, tipo_cuenta) REFERENCES cuentas_ahorros(id, tipo),
    
    CONSTRAINT uk_cuenta_periodo UNIQUE (cuenta_id, periodo_fiscal)
);

CREATE INDEX idx_plan_aportaciones_cuenta ON plan_aportaciones_mensuales(cuenta_id, periodo_fiscal);


-- 5. HISTORIAL TRANSACCIONAL (LEDGER / LIBRO MAYOR)

CREATE TABLE transacciones_ledger (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    cuenta_id INT NOT NULL,
    tipo_transaccion tipo_movimiento NOT NULL,
    monto NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    saldo_anterior NUMERIC(15,2) NOT NULL,
    saldo_resultante NUMERIC(15,2) NOT NULL,
    canal VARCHAR(20) NOT NULL CHECK (canal IN ('VENTANILLA', 'APP_MOVIL', 'WEB_SOCIO', 'PROCESO_BATCH')),
    referencia VARCHAR(100),
    descripcion TEXT NOT NULL,
    usuario_admin_id INT REFERENCES usuarios_admin(id),
    fecha_contable TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    direccion_ip VARCHAR(45) NOT NULL,
    dispositivo_info TEXT,
    
    -- Seguridad SaaS: La cuenta contable y la transaccion deben pertenecer a la misma empresa
    CONSTRAINT fk_ledger_cuenta_empresa FOREIGN KEY (cuenta_id, empresa_id) REFERENCES cuentas_ahorros(id, empresa_id),
    CONSTRAINT uk_empresa_referencia UNIQUE (empresa_id, referencia), -- Corregido: Unicidad por Cooperativa
    CONSTRAINT uk_ledger_empresa UNIQUE (id, empresa_id),
    
    -- Restriccion de consistencia matematica del saldo
    CONSTRAINT chk_saldo_consistencia CHECK (
        (tipo_transaccion = 'CREDITO' AND saldo_resultante = saldo_anterior + monto) OR
        (tipo_transaccion = 'DEBITO' AND saldo_resultante = saldo_anterior - monto)
    )
);


-- 6. DEPÓSITOS A PLAZO FIJO (PÓLIZAS)

CREATE TABLE depositos_plazo_fijo (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    socio_id INT NOT NULL,
    numero_poliza VARCHAR(20) NOT NULL,
    monto_principal NUMERIC(15,2) NOT NULL CHECK (monto_principal > 0),
    plazo_dias INT NOT NULL CHECK (plazo_dias >= 30),
    tasa_interes_anual NUMERIC(5,2) NOT NULL CHECK (tasa_interes_anual > 0),
    
    -- Fórmula de Interés Simple Comercial: I = C * i * t / 360
    interes_ganado NUMERIC(15,2) GENERATED ALWAYS AS (
        ROUND((monto_principal * (tasa_interes_anual / 100.0) * (plazo_dias / 360.0)), 2)
    ) STORED,
    
    monto_final NUMERIC(15,2) GENERATED ALWAYS AS (
        monto_principal + ROUND((monto_principal * (tasa_interes_anual / 100.0) * (plazo_dias / 360.0)), 2)
    ) STORED,
    
    fecha_constitucion DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_vencimiento DATE NOT NULL,
    estado estado_dpf DEFAULT 'CONSTITUIDO',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Aislamiento de negocio
    CONSTRAINT fk_dpf_socio_empresa FOREIGN KEY (socio_id, empresa_id) REFERENCES socios(id, empresa_id),
    CONSTRAINT uk_empresa_numero_poliza UNIQUE (empresa_id, numero_poliza), -- Corregido: Unicidad por Cooperativa
    CONSTRAINT chk_fechas_dpf CHECK (fecha_vencimiento = fecha_constitucion + plazo_dias)
);


-- 7. ASISTENCIA CONTABLE Y PARTIDA DOBLE

CREATE TABLE asientos_cabecera (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id) ON DELETE RESTRICT,
    transaccion_ledger_id BIGINT,
    numero_asiento VARCHAR(30) NOT NULL, -- Código correlativo del periodo (Ej: AS-2026-0001)
    glosa TEXT NOT NULL,
    fecha_asiento TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_cabecera_ledger_empresa FOREIGN KEY (transaccion_ledger_id, empresa_id) REFERENCES transacciones_ledger(id, empresa_id) ON DELETE SET NULL,
    CONSTRAINT uk_empresa_asiento UNIQUE (empresa_id, numero_asiento),
    CONSTRAINT uk_cabecera_empresa UNIQUE (id, empresa_id)
);

CREATE TABLE asientos_detalle (
    id BIGSERIAL PRIMARY KEY,
    asiento_cabecera_id BIGINT NOT NULL,
    plan_cuentas_id INT NOT NULL,
    tipo_asiento VARCHAR(7) NOT NULL CHECK (tipo_asiento IN ('DEBITO', 'CREDITO')), -- DEBITO = Debe, CREDITO = Haber
    monto NUMERIC(15,2) NOT NULL CHECK (monto > 0),
    empresa_id INT NOT NULL, -- Propagada para validacion
    
    -- Claves compuestas para forzar que el asiento y la cuenta contable compartan la misma cooperativa
    CONSTRAINT fk_detalle_cabecera_empresa FOREIGN KEY (asiento_cabecera_id, empresa_id) REFERENCES asientos_cabecera(id, empresa_id) ON DELETE CASCADE,
    CONSTRAINT fk_detalle_plan_empresa FOREIGN KEY (plan_cuentas_id, empresa_id) REFERENCES plan_cuentas(id, empresa_id) ON DELETE RESTRICT,
    CONSTRAINT uk_asiento_cuenta UNIQUE (asiento_cabecera_id, plan_cuentas_id, tipo_asiento)
);

CREATE INDEX idx_asientos_cabecera_empresa ON asientos_cabecera(empresa_id, fecha_asiento);
CREATE INDEX idx_asientos_detalle_cabecera ON asientos_detalle(asiento_cabecera_id);


-- 8. MOTOR DE CRÉDITOS Y COLOCACIONES

CREATE TYPE sistema_amortizacion AS ENUM ('FRANCES', 'ALEMAN', 'AMERICANO');
CREATE TYPE estado_credito AS ENUM ('SOLICITADO', 'EN_REVISION', 'APROBADO', 'RECHAZADO', 'DESEMBOLSADO', 'CANCELADO', 'EN_MORA');
CREATE TYPE estado_cuota AS ENUM ('PENDIENTE', 'PAGADA', 'EN_MORA', 'PAGADA_ATRASADO');

CREATE TABLE creditos (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    socio_id INT NOT NULL,
    numero_credito VARCHAR(20) NOT NULL,
    monto_solicitado NUMERIC(15,2) NOT NULL CHECK (monto_solicitado > 0),
    monto_desembolsado NUMERIC(15,2) DEFAULT 0.00 CHECK (monto_desembolsado >= 0),
    plazo_meses INT NOT NULL CHECK (plazo_meses > 0),
    tasa_interes_anual NUMERIC(5,2) NOT NULL CHECK (tasa_interes_anual > 0),
    tasa_mora_anual NUMERIC(5,2) NOT NULL CHECK (tasa_mora_anual >= 0),
    tipo_amortizacion sistema_amortizacion NOT NULL,
    garantia_descripcion TEXT NOT NULL,
    estado estado_credito DEFAULT 'SOLICITADO',
    fecha_solicitud TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_desembolso TIMESTAMP,
    usuario_oficial_id INT REFERENCES usuarios_admin(id),
    
    CONSTRAINT fk_credito_socio_empresa FOREIGN KEY (socio_id, empresa_id) REFERENCES socios(id, empresa_id),
    CONSTRAINT uk_empresa_numero_credito UNIQUE (empresa_id, numero_credito), -- Corregido: Unicidad por Cooperativa
    CONSTRAINT uk_creditos_empresa UNIQUE (id, empresa_id),
    CONSTRAINT chk_monto_desembolso CHECK (monto_desembolsado <= monto_solicitado)
);

CREATE TABLE cuotas_amortizacion (
    id BIGSERIAL PRIMARY KEY,
    credito_id INT NOT NULL REFERENCES creditos(id) ON DELETE RESTRICT,
    numero_cuota INT NOT NULL CHECK (numero_cuota > 0),
    fecha_vencimiento DATE NOT NULL,
    
    capital_proyectado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (capital_proyectado >= 0),
    interes_proyectado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (interes_proyectado >= 0),
    cuota_total_proyectada NUMERIC(15,2) GENERATED ALWAYS AS (capital_proyectado + interes_proyectado) STORED,
    
    capital_pagado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (capital_pagado >= 0),
    interes_pagado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (interes_pagado >= 0),
    
    monto_mora_acumulado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (monto_mora_acumulado >= 0),
    monto_mora_pagado NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (monto_mora_pagado >= 0),
    dias_atraso INT NOT NULL DEFAULT 0 CHECK (dias_atraso >= 0),
    
    estado estado_cuota DEFAULT 'PENDIENTE',
    fecha_ultimo_pago TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_credito_cuota UNIQUE (credito_id, numero_cuota)
);

CREATE TABLE historial_estados_credito (
    id SERIAL PRIMARY KEY,
    credito_id INT NOT NULL REFERENCES creditos(id) ON DELETE CASCADE,
    estado_anterior estado_credito,
    estado_nuevo estado_credito NOT NULL,
    observaciones TEXT NOT NULL,
    usuario_admin_id INT NOT NULL REFERENCES usuarios_admin(id),
    fecha_cambio TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- 9. TRAZABILIDAD Y AUDITORÍA

CREATE TABLE logs_auditoria (
    id BIGSERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    usuario_admin_id INT REFERENCES usuarios_admin(id),
    socio_id INT REFERENCES socios(id) ON DELETE RESTRICT,
    
    accion VARCHAR(100) NOT NULL,
    tabla_afectada VARCHAR(50) NOT NULL,
    registro_id INT NOT NULL,
    
    valor_anterior JSONB,
    valor_nuevo JSONB,
    
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    direccion_ip VARCHAR(45) NOT NULL,
    dispositivo_info TEXT NOT NULL,
    
    CONSTRAINT chk_actor_auditoria CHECK (usuario_admin_id IS NOT NULL OR socio_id IS NOT NULL)
);

CREATE TABLE control_sesiones (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    usuario_admin_id INT REFERENCES usuarios_admin(id),
    socio_id INT REFERENCES socios(id) ON DELETE RESTRICT,
    token_jwt_hash VARCHAR(64) NOT NULL UNIQUE,
    fecha_inicio TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_expiracion TIMESTAMP NOT NULL,
    ultima_actividad TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    direccion_ip VARCHAR(45) NOT NULL,
    dispositivo_info TEXT NOT NULL,
    estado VARCHAR(20) DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA', 'CERRADA', 'EXPIRADA', 'REVOCADA')),
    
    CONSTRAINT chk_actor_sesion CHECK (usuario_admin_id IS NOT NULL OR socio_id IS NOT NULL)
);


-- 10. CIERRE ANUAL Y DISTRIBUCIÓN DE EXCEDENTES

CREATE TABLE cierres_anuales (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id) ON DELETE RESTRICT,
    anio_fiscal INT NOT NULL,
    total_ingresos NUMERIC(15,2) NOT NULL CHECK (total_ingresos >= 0),
    total_gastos NUMERIC(15,2) NOT NULL CHECK (total_gastos >= 0),
    total_provisiones NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (total_provisiones >= 0),
    
    -- Validacion de consistencia del excedente neto
    excedente_neto NUMERIC(15,2) NOT NULL,
    fecha_cierre TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usuario_admin_id INT NOT NULL REFERENCES usuarios_admin(id),
    
    CONSTRAINT uk_empresa_anio UNIQUE (empresa_id, anio_fiscal),
    CONSTRAINT chk_excedente_neto CHECK (excedente_neto = total_ingresos - total_gastos - total_provisiones)
);

CREATE TABLE distribucion_excedentes_socios (
    id SERIAL PRIMARY KEY,
    cierre_anual_id INT NOT NULL REFERENCES cierres_anuales(id) ON DELETE CASCADE,
    socio_id INT NOT NULL,
    cuenta_destino_id INT NOT NULL,
    monto_aportaciones_socio NUMERIC(15,2) NOT NULL,
    monto_excedente_asignado NUMERIC(15,2) NOT NULL CHECK (monto_excedente_asignado >= 0),
    fecha_procesado TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Aislamiento de socio y cuenta en distribucion (el socio es dueño de la cuenta receptora)
    empresa_id INT NOT NULL,
    CONSTRAINT fk_distribucion_socio FOREIGN KEY (socio_id, empresa_id) REFERENCES socios(id, empresa_id),
    CONSTRAINT fk_distribucion_cuenta FOREIGN KEY (cuenta_destino_id, empresa_id) REFERENCES cuentas_ahorros(id, empresa_id),
    CONSTRAINT uk_cierre_socio UNIQUE (cierre_anual_id, socio_id)
);

CREATE INDEX idx_excedentes_socio ON distribucion_excedentes_socios(socio_id);
CREATE INDEX idx_logs_auditoria_empresa ON logs_auditoria(empresa_id, fecha);
CREATE INDEX idx_control_sesiones_token ON control_sesiones(token_jwt_hash);


-- 10.1 CADASTRADO Y CONTROL DE CAJA DIARIA
CREATE TABLE cajas_diarias (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    usuario_cajero_id INT NOT NULL REFERENCES usuarios_admin(id),
    fecha_contable DATE NOT NULL,
    monto_apertura NUMERIC(15,2) NOT NULL CHECK (monto_apertura >= 0),
    monto_cierre_sistema NUMERIC(15,2) NOT NULL DEFAULT 0.00 CHECK (monto_cierre_sistema >= 0),
    monto_cierre_efectivo_real NUMERIC(15,2) CHECK (monto_cierre_efectivo_real >= 0),
    diferencia NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(15) NOT NULL DEFAULT 'APERTURADA' CHECK (estado IN ('APERTURADA', 'CERRADA')),
    asiento_cabecera_id BIGINT REFERENCES asientos_cabecera(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT uk_empresa_cajero_fecha UNIQUE (empresa_id, usuario_cajero_id, fecha_contable)
);

CREATE INDEX idx_cajas_diarias_cajero_fecha ON cajas_diarias(empresa_id, usuario_cajero_id, fecha_contable);

-- 10.2 REGISTRO DE DEVENGO Y CAPITALIZACIÓN DIARIA/MENSUAL
CREATE TABLE devengos_registro (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    fecha_devengo DATE NOT NULL,
    total_devengado NUMERIC(15,2) NOT NULL,
    asiento_cabecera_id BIGINT REFERENCES asientos_cabecera(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_empresa_fecha_devengo UNIQUE (empresa_id, fecha_devengo)
);

CREATE TABLE capitalizaciones_registro (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    anio INT NOT NULL,
    mes INT NOT NULL,
    fecha_capitalizacion DATE NOT NULL,
    total_capitalizado NUMERIC(15,2) NOT NULL,
    asiento_cabecera_id BIGINT REFERENCES asientos_cabecera(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_empresa_anio_mes UNIQUE (empresa_id, anio, mes)
);

CREATE TABLE tokens_recuperacion (
    id SERIAL PRIMARY KEY,
    empresa_id INT NOT NULL REFERENCES empresas(id),
    socio_id INT NOT NULL REFERENCES socios(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    canal VARCHAR(10) NOT NULL CHECK (canal IN ('CORREO', 'SMS')),
    fecha_expiracion TIMESTAMP NOT NULL,
    utilizado BOOLEAN NOT NULL DEFAULT FALSE,
    intentos_fallidos INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_empresa_token_hash UNIQUE (empresa_id, token_hash)
);



-- 11. TRIGGER POSTGRESQL PARA ENFORZAR LA PARTIDA DOBLE (A NIVEL DE BDD)

CREATE OR REPLACE FUNCTION fn_validar_partida_doble() 
RETURNS TRIGGER AS $$
DECLARE
    l_debitos NUMERIC(15,2) := 0;
    l_creditos NUMERIC(15,2) := 0;
    l_asiento_id BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        l_asiento_id := OLD.asiento_cabecera_id;
    ELSE
        l_asiento_id := NEW.asiento_cabecera_id;
    END IF;

    -- Calcular debitos y creditos del asiento actual en la transaccion
    SELECT COALESCE(SUM(monto), 0) INTO l_debitos FROM asientos_detalle 
    WHERE asiento_cabecera_id = l_asiento_id AND tipo_asiento = 'DEBITO';

    SELECT COALESCE(SUM(monto), 0) INTO l_creditos FROM asientos_detalle 
    WHERE asiento_cabecera_id = l_asiento_id AND tipo_asiento = 'CREDITO';

    -- Si hay alguna linea, validar que cuadre.
    -- (Por ser trigger DEFERRABLE, la evaluacion se realiza al final de la transaccion, permitiendo registrar debito y credito por separado antes del commit)
    IF (l_debitos + l_creditos > 0) AND (l_debitos != l_creditos) THEN
        RAISE EXCEPTION 'Fallo de Partida Doble en Asiento ID %: Total Debitos ($%) no coincide con Total Creditos ($%)', 
            l_asiento_id, l_debitos, l_creditos;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Trigger Diferido para evaluacion al momento del COMMIT
CREATE CONSTRAINT TRIGGER trg_validar_partida_doble
AFTER INSERT OR UPDATE OR DELETE ON asientos_detalle
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION fn_validar_partida_doble();


--========== DATOS DE PRUEBA INICIALES (CON CONTRASENAS ENCRIPTADAS POR BCRYPT) ============ --

-- 1. Insertar la Cooperativa de Prueba (Tenant ID = 1)
INSERT INTO empresas (id, ruc, razon_social, nombre_comercial, codigo_seps, representante_legal, cedula_representante, moneda, estado)
VALUES (1, '1791234567001', 'Cooperativa de Ahorro y Crédito ITQ', 'Cooperativa de Ahorro y Crédito ITQ', 'SEPS-REG-2026-001', 'Santiago Administrador', '1710034065', 'USD', 'ACTIVO');

-- 2. Insertar un Usuario Administrativo (Contraseña hasheada de 'CoopSF2026!')
INSERT INTO usuarios_admin (id, empresa_id, username, password_hash, nombres_completos, correo, rol, estado)
VALUES (1, 1, 'oficial_santiago', '$2a$10$.mUVoUfWa9YO757.5gNMbOpqYUIYqFvL76fHcny7vbgWA3EPcIMKq', 'Santiago Oficial de Crédito', 'santiago@itq.edu.ec', 'OFICIAL_DE_CREDITO', 'ACTIVO');

-- 3. Socio de Prueba (Cédula ecuatoriana válida modulo 10)
INSERT INTO socios (id, empresa_id, tipo_identificacion, identificacion, nombres_completos, direccion, telefono, correo, actividad_economica, ingresos_mensuales, gastos_mensuales, deudas_actuales, estado)
VALUES (1, 1, 'C', '1710034065', 'Juan Pérez Socio Test', 'Av. Antonio de Ulloa N28-30, Quito', '0991234567', 'juan.perez@test.com', 'Desarrollador de Software', 1200.00, 400.00, 100.00, 'ACTIVO');

-- 4. Crear la Cuenta de Ahorros del Socio (Saldo inicial en $0.00)
INSERT INTO cuentas_ahorros (id, empresa_id, socio_id, numero_cuenta, tipo, saldo, estado)
VALUES (1, 1, 1, '401010000001', 'AHORRO_VISTA', 0.00, 'ACTIVA');

-- 5. Insertar un Crédito en estado APROBADO (Listo para ser desembolsado)
INSERT INTO creditos (id, empresa_id, socio_id, numero_credito, monto_solicitado, plazo_meses, tasa_interes_anual, tasa_mora_anual, tipo_amortizacion, garantia_descripcion, estado, usuario_oficial_id)
VALUES (1, 1, 1, 'CRE-2026-0001', 3000.00, 6, 12.00, 5.00, 'FRANCES', 'Garantía Quirografaria - Firma de Pagaré', 'APROBADO', 1);

-- 6. Inyección del Plan de Cuentas Inicial para la Cooperativa (Tenant ID = 1)
INSERT INTO plan_cuentas (id, empresa_id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento) VALUES
(1, 1, '1', 'ACTIVOS', 'ACTIVO', FALSE),
(2, 1, '1.1', 'FONDOS DISPONIBLES', 'ACTIVO', FALSE),
(3, 1, '1.1.01', 'CAJA', 'ACTIVO', FALSE),
(4, 1, '1.1.01.05', 'Caja General Ventanilla', 'ACTIVO', TRUE),
(5, 1, '1.4', 'CARTERA DE CREDITOS', 'ACTIVO', FALSE),
(6, 1, '1.4.01', 'Cartera de Créditos por Desembolsar', 'ACTIVO', TRUE),
(7, 1, '2', 'PASIVOS', 'PASIVO', FALSE),
(8, 1, '2.1', 'OBLIGACIONES CON EL PUBLICO', 'PASIVO', FALSE),
(9, 1, '2.1.01', 'DEPOSITOS A LA VISTA', 'PASIVO', FALSE),
(10, 1, '2.1.01.05', 'Cuentas de Ahorros de Socios', 'PASIVO', TRUE),
(11, 1, '3', 'PATRIMONIO', 'PATRIMONIO', FALSE),
(12, 1, '3.1', 'CAPITAL SOCIAL', 'PATRIMONIO', FALSE),
(13, 1, '3.1.01', 'Capital Social Numerario', 'PATRIMONIO', FALSE),
(14, 1, '3.1.01.05', 'Aportaciones Obligatorias de Socios', 'PATRIMONIO', TRUE),
-- 4. INGRESOS
(15, 1, '5', 'INGRESOS', 'INGRESO', FALSE),
(16, 1, '5.1', 'INGRESOS FINANCIEROS', 'INGRESO', FALSE),
(17, 1, '5.1.01', 'INGRESOS POR INTERESES DE CARTERA', 'INGRESO', FALSE),
(18, 1, '5.1.01.05', 'Intereses Cartera de Créditos Vigente', 'INGRESO', TRUE),
(19, 1, '1.1.01.01', 'Bóveda General de la Cooperativa', 'ACTIVO', TRUE),
(20, 1, '1.2.99.01', 'Cuentas por Cobrar Empleados (Faltantes de Caja)', 'ACTIVO', TRUE),
(21, 1, '5.2.99.01', 'Otros Ingresos - Sobrantes de Caja', 'INGRESO', TRUE),
(22, 1, '2.1.01.10', 'Intereses por Pagar Obligaciones con Socios', 'PASIVO', TRUE),
(23, 1, '4', 'GASTOS', 'GASTO', FALSE),
(24, 1, '4.1', 'GASTOS FINANCIEROS', 'GASTO', FALSE),
(25, 1, '4.1.01', 'GASTOS POR OBLIGACIONES CON EL PUBLICO', 'GASTO', FALSE),
(26, 1, '4.1.01.05', 'Gastos de Intereses Obligaciones con Socios', 'GASTO', TRUE);

-- Ajustar los valores de los seriales para evitar fallas de duplicidad en inserciones posteriores
SELECT setval('empresas_id_seq', 1);
SELECT setval('usuarios_admin_id_seq', 1);
SELECT setval('socios_id_seq', 1);
SELECT setval('cuentas_ahorros_id_seq', 1);
SELECT setval('creditos_id_seq', 1);
SELECT setval('plan_cuentas_id_seq', 26);

