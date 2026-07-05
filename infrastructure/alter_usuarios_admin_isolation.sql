-- Script de Migración para el Aislamiento de Unicidad de Usuarios/Empleados en Multi-Tenant
-- 1. Eliminar la restricción de unicidad global sobre la identificación del administrador
ALTER TABLE usuarios_admin DROP CONSTRAINT IF EXISTS uk_usuarios_admin_identificacion;

-- 2. Crear restricción de unicidad compuesta por inquilino (empresa_id + identificacion)
ALTER TABLE usuarios_admin ADD CONSTRAINT uk_usuarios_admin_empresa_identificacion UNIQUE (empresa_id, identificacion);
