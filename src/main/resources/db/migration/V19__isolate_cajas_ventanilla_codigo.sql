ALTER TABLE cajas_ventanilla DROP CONSTRAINT IF EXISTS cajas_ventanilla_codigo_key;
ALTER TABLE cajas_ventanilla DROP CONSTRAINT IF EXISTS uk_empresa_caja_codigo;
ALTER TABLE cajas_ventanilla ADD CONSTRAINT uk_empresa_caja_codigo UNIQUE (empresa_id, codigo);
