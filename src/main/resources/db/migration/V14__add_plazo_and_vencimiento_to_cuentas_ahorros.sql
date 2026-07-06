-- 1. Agregar columnas plazo_dias, fecha_vencimiento y renovacion_automatica a la tabla cuentas_ahorros
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS plazo_dias INT;
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS fecha_vencimiento DATE;
ALTER TABLE cuentas_ahorros ADD COLUMN IF NOT EXISTS renovacion_automatica BOOLEAN DEFAULT FALSE;
