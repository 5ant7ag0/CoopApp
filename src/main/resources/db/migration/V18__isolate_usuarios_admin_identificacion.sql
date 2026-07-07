ALTER TABLE usuarios_admin DROP CONSTRAINT IF EXISTS uk_usuarios_admin_identificacion;
ALTER TABLE usuarios_admin ADD CONSTRAINT uk_empresa_identificacion_admin UNIQUE (empresa_id, identificacion);
