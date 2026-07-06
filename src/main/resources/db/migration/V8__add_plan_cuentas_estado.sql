-- 1. Agregar columna estado a plan_cuentas para compatibilidad con la entidad JPA PlanCuentas.java
ALTER TABLE plan_cuentas ADD COLUMN IF NOT EXISTS estado VARCHAR(20) DEFAULT 'ACTIVO' NOT NULL;
