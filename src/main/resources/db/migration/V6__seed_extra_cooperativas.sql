-- 1. Insertar Cooperativa Progreso (Tenant ID = 10)
INSERT INTO empresas (id, ruc, razon_social, nombre_comercial, codigo_seps, representante_legal, cedula_representante, moneda, estado, segmento_seps, correo_institucional, correo_gerente)
VALUES (10, '1792233445001', 'Cooperativa Progreso Ltda', 'Coop Progreso', 'SEPS-REG-2026-010', 'Gerente Progreso', '1710034065', 'USD', 'ACTIVO', 'SEGMENTO 1', 'contacto@progreso.com', 'gerente@progreso.com');

-- 2. Insertar Cooperativa Crecer (Tenant ID = 11)
INSERT INTO empresas (id, ruc, razon_social, nombre_comercial, codigo_seps, representante_legal, cedula_representante, moneda, estado, segmento_seps, correo_institucional, correo_gerente)
VALUES (11, '1799999999001', 'Coop Crecer', 'Coop Crecer', 'SEPS-REG-2026-011', 'Gerente Crecer', '1710034065', 'USD', 'ACTIVO', 'SEGMENTO 1', 'contacto@crecer.com', 'gerente@crecer.com');

-- 3. Insertar Cooperativa Quito (Tenant ID = 12)
INSERT INTO empresas (id, ruc, razon_social, nombre_comercial, codigo_seps, representante_legal, cedula_representante, moneda, estado, segmento_seps, correo_institucional, correo_gerente)
VALUES (12, '1799999991001', 'Cooperativa Quito', 'Coop Quito', 'SEPS-REG-2026-012', 'Gerente Quito', '1710034065', 'USD', 'ACTIVO', 'SEGMENTO 1', 'contacto@quito.com', 'gerente@quito.com');

-- Ajustar la secuencia de IDs de empresas
SELECT setval('empresas_id_seq', 12);

-- 4. Inyección de Plan de Cuentas Inicial para las nuevas cooperativas
-- Cooperativa Progreso (10)
INSERT INTO plan_cuentas (empresa_id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento) VALUES
(10, '1', 'ACTIVOS', 'ACTIVO', FALSE),
(10, '1.1', 'FONDOS DISPONIBLES', 'ACTIVO', FALSE),
(10, '1.1.01', 'CAJA', 'ACTIVO', FALSE),
(10, '1.1.01.05', 'Caja General Ventanilla', 'ACTIVO', TRUE),
(10, '1.4', 'CARTERA DE CREDITOS', 'ACTIVO', FALSE),
(10, '1.4.01', 'Cartera de Créditos por Desembolsar', 'ACTIVO', TRUE),
(10, '2', 'PASIVOS', 'PASIVO', FALSE),
(10, '2.1', 'OBLIGACIONES CON EL PUBLICO', 'PASIVO', FALSE),
(10, '2.1.01', 'DEPOSITOS A LA VISTA', 'PASIVO', FALSE),
(10, '2.1.01.05', 'Cuentas de Ahorros de Socios', 'PASIVO', TRUE),
(10, '3', 'PATRIMONIO', 'PATRIMONIO', FALSE),
(10, '3.1', 'CAPITAL SOCIAL', 'PATRIMONIO', FALSE),
(10, '3.1.01', 'Capital Social Numerario', 'PATRIMONIO', FALSE),
(10, '3.1.01.05', 'Aportaciones Obligatorias de Socios', 'PATRIMONIO', TRUE),
(10, '5', 'INGRESOS', 'INGRESO', FALSE),
(10, '5.1', 'INGRESOS FINANCIEROS', 'INGRESO', FALSE),
(10, '5.1.01', 'INGRESOS POR INTERESES DE CARTERA', 'INGRESO', FALSE),
(10, '5.1.01.05', 'Intereses Cartera de Créditos Vigente', 'INGRESO', TRUE),
(10, '1.1.01.01', 'Bóveda General de la Cooperativa', 'ACTIVO', TRUE),
(10, '1.2.99.01', 'Cuentas por Cobrar Empleados (Faltantes de Caja)', 'ACTIVO', TRUE),
(10, '5.2.99.01', 'Otros Ingresos - Sobrantes de Caja', 'INGRESO', TRUE),
(10, '2.1.01.10', 'Intereses por Pagar Obligaciones con Socios', 'PASIVO', TRUE),
(10, '4', 'GASTOS', 'GASTO', FALSE),
(10, '4.1', 'GASTOS FINANCIEROS', 'GASTO', FALSE),
(10, '4.1.01', 'GASTOS POR OBLIGACIONES CON EL PUBLICO', 'GASTO', FALSE),
(10, '4.1.01.05', 'Gastos de Intereses Obligaciones con Socios', 'GASTO', TRUE);

-- Cooperativa Crecer (11)
INSERT INTO plan_cuentas (empresa_id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento) VALUES
(11, '1', 'ACTIVOS', 'ACTIVO', FALSE),
(11, '1.1', 'FONDOS DISPONIBLES', 'ACTIVO', FALSE),
(11, '1.1.01', 'CAJA', 'ACTIVO', FALSE),
(11, '1.1.01.05', 'Caja General Ventanilla', 'ACTIVO', TRUE),
(11, '1.4', 'CARTERA DE CREDITOS', 'ACTIVO', FALSE),
(11, '1.4.01', 'Cartera de Créditos por Desembolsar', 'ACTIVO', TRUE),
(11, '2', 'PASIVOS', 'PASIVO', FALSE),
(11, '2.1', 'OBLIGACIONES CON EL PUBLICO', 'PASIVO', FALSE),
(11, '2.1.01', 'DEPOSITOS A LA VISTA', 'PASIVO', FALSE),
(11, '2.1.01.05', 'Cuentas de Ahorros de Socios', 'PASIVO', TRUE),
(11, '3', 'PATRIMONIO', 'PATRIMONIO', FALSE),
(11, '3.1', 'CAPITAL SOCIAL', 'PATRIMONIO', FALSE),
(11, '3.1.01', 'Capital Social Numerario', 'PATRIMONIO', FALSE),
(11, '3.1.01.05', 'Aportaciones Obligatorias de Socios', 'PATRIMONIO', TRUE),
(11, '5', 'INGRESOS', 'INGRESO', FALSE),
(11, '5.1', 'INGRESOS FINANCIEROS', 'INGRESO', FALSE),
(11, '5.1.01', 'INGRESOS POR INTERESES DE CARTERA', 'INGRESO', FALSE),
(11, '5.1.01.05', 'Intereses Cartera de Créditos Vigente', 'INGRESO', TRUE),
(11, '1.1.01.01', 'Bóveda General de la Cooperativa', 'ACTIVO', TRUE),
(11, '1.2.99.01', 'Cuentas por Cobrar Empleados (Faltantes de Caja)', 'ACTIVO', TRUE),
(11, '5.2.99.01', 'Otros Ingresos - Sobrantes de Caja', 'INGRESO', TRUE),
(11, '2.1.01.10', 'Intereses por Pagar Obligaciones con Socios', 'PASIVO', TRUE),
(11, '4', 'GASTOS', 'GASTO', FALSE),
(11, '4.1', 'GASTOS FINANCIEROS', 'GASTO', FALSE),
(11, '4.1.01', 'GASTOS POR OBLIGACIONES CON EL PUBLICO', 'GASTO', FALSE),
(11, '4.1.01.05', 'Gastos de Intereses Obligaciones con Socios', 'GASTO', TRUE);

-- Cooperativa Quito (12)
INSERT INTO plan_cuentas (empresa_id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento) VALUES
(12, '1', 'ACTIVOS', 'ACTIVO', FALSE),
(12, '1.1', 'FONDOS DISPONIBLES', 'ACTIVO', FALSE),
(12, '1.1.01', 'CAJA', 'ACTIVO', FALSE),
(12, '1.1.01.05', 'Caja General Ventanilla', 'ACTIVO', TRUE),
(12, '1.4', 'CARTERA DE CREDITOS', 'ACTIVO', FALSE),
(12, '1.4.01', 'Cartera de Créditos por Desembolsar', 'ACTIVO', TRUE),
(12, '2', 'PASIVOS', 'PASIVO', FALSE),
(12, '2.1', 'OBLIGACIONES CON EL PUBLICO', 'PASIVO', FALSE),
(12, '2.1.01', 'DEPOSITOS A LA VISTA', 'PASIVO', FALSE),
(12, '2.1.01.05', 'Cuentas de Ahorros de Socios', 'PASIVO', TRUE),
(12, '3', 'PATRIMONIO', 'PATRIMONIO', FALSE),
(12, '3.1', 'CAPITAL SOCIAL', 'PATRIMONIO', FALSE),
(12, '3.1.01', 'Capital Social Numerario', 'PATRIMONIO', FALSE),
(12, '3.1.01.05', 'Aportaciones Obligatorias de Socios', 'PATRIMONIO', TRUE),
(12, '5', 'INGRESOS', 'INGRESO', FALSE),
(12, '5.1', 'INGRESOS FINANCIEROS', 'INGRESO', FALSE),
(12, '5.1.01', 'INGRESOS POR INTERESES DE CARTERA', 'INGRESO', FALSE),
(12, '5.1.01.05', 'Intereses Cartera de Créditos Vigente', 'INGRESO', TRUE),
(12, '1.1.01.01', 'Bóveda General de la Cooperativa', 'ACTIVO', TRUE),
(12, '1.2.99.01', 'Cuentas por Cobrar Empleados (Faltantes de Caja)', 'ACTIVO', TRUE),
(12, '5.2.99.01', 'Otros Ingresos - Sobrantes de Caja', 'INGRESO', TRUE),
(12, '2.1.01.10', 'Intereses por Pagar Obligaciones con Socios', 'PASIVO', TRUE),
(12, '4', 'GASTOS', 'GASTO', FALSE),
(12, '4.1', 'GASTOS FINANCIEROS', 'GASTO', FALSE),
(12, '4.1.01', 'GASTOS POR OBLIGACIONES CON EL PUBLICO', 'GASTO', FALSE),
(12, '4.1.01.05', 'Gastos de Intereses Obligaciones con Socios', 'GASTO', TRUE);


-- 5. Insertar Usuarios Administrativos (Contraseña: CoopSF2026!)
INSERT INTO usuarios_admin (empresa_id, username, password_hash, nombres_completos, correo, rol, estado, identificacion) VALUES
(10, 'admin_progreso', '$2a$10$.mUVoUfWa9YO757.5gNMbOpqYUIYqFvL76fHcny7vbgWA3EPcIMKq', 'Administrador Progreso', 'admin@progreso.com', 'GERENTE_GENERAL', 'ACTIVO', '1724032136'),
(11, 'admin_crecer', '$2a$10$.mUVoUfWa9YO757.5gNMbOpqYUIYqFvL76fHcny7vbgWA3EPcIMKq', 'Administrador Crecer', 'admin@crecer.com', 'GERENTE_GENERAL', 'ACTIVO', '1724032144'),
(12, 'admin_quito', '$2a$10$.mUVoUfWa9YO757.5gNMbOpqYUIYqFvL76fHcny7vbgWA3EPcIMKq', 'Administrador Quito', 'admin@quito.com', 'GERENTE_GENERAL', 'ACTIVO', '1724032151');
