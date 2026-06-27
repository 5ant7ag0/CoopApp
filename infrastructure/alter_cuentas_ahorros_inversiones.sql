-- Script de migración incremental: Vencimiento de Inversiones (Normativa SEPS)
-- Agrega columnas de control de plazos y vencimientos a la tabla cuentas_ahorros

ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS plazo_dias INT;
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS fecha_vencimiento DATE;
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS renovacion_automatica BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN cuentas_ahorros.plazo_dias IS 'Plazo total de la inversión contratada en días';
COMMENT ON COLUMN cuentas_ahorros.fecha_vencimiento IS 'Fecha exacta en la que vence el contrato de la inversión';
COMMENT ON COLUMN cuentas_ahorros.renovacion_automatica IS 'Determina si al vencer se renueva automáticamente el capital';
