package com.cooperativa.core.service;

import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.security.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TenantSeedingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private NotificacionService notificacionService;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void seedTenantData(Empresa savedEmpresa, UsuariosAdmin admin) {
        // 4. Preparar el Administrador Inicial
        if (admin.getUsername() == null || admin.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Error: El nombre de usuario del administrador no puede estar vacio.");
        }

        // Encriptamos una clave UUID aleatoria para evitar contraseñas por defecto
        String passwordHash = encryptionService.hashPassword(UUID.randomUUID().toString());
        
        // Forzar rol administrativo base para el onboarding (por ejemplo GERENTE_GENERAL)
        String rol = admin.getRol();
        if (rol == null || rol.trim().isEmpty()) {
            rol = "GERENTE_GENERAL";
        }
        String estado = "ACTIVO";

        // Mapear los campos personales y de contacto del Gerente General a partir del Representante Legal
        String nombresCompletos = savedEmpresa.getRepresentanteLegal();
        String correo = savedEmpresa.getCorreoGerente();
        String identificacion = savedEmpresa.getCedulaRepresentante();

        // Usamos JdbcTemplate para evitar la verificación del TenantId de Hibernate en BaseEntity
        Integer adminId = jdbcTemplate.queryForObject(
            "INSERT INTO usuarios_admin (empresa_id, username, password_hash, nombres_completos, correo, rol, estado, identificacion, cambiar_password_proximo_inicio, limite_transaccion_max, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id",
            Integer.class,
            savedEmpresa.getId(), admin.getUsername(), passwordHash,
            nombresCompletos, correo, rol,
            estado, identificacion, true,
            java.math.BigDecimal.ZERO
        );

        // Generar Token de activación UUID de un solo uso
        String tokenRaw = UUID.randomUUID().toString();
        String tokenHash = hashSha256(tokenRaw);

        jdbcTemplate.update(
            "INSERT INTO tokens_recuperacion (usuario_admin_id, token_hash, canal, fecha_expiracion, utilizado, intentos_fallidos, empresa_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
            adminId, tokenHash, "CORREO", LocalDateTime.now().plusMinutes(15), false, 0, savedEmpresa.getId()
        );

        // Despachar correo de bienvenida con enlace seguro al correo personal del Representante Legal
        if (savedEmpresa.getCorreoGerente() != null && !savedEmpresa.getCorreoGerente().isEmpty()) {
            String linkActivacion = frontendUrl + "/recuperar-clave?token=" + tokenRaw + "&identificacion=" + admin.getUsername() + "&tenantId=" + savedEmpresa.getId() + "&activar=true";
            notificacionService.enviarCredencialesSaaS(
                savedEmpresa.getCorreoGerente(), 
                savedEmpresa.getRazonSocial(), 
                admin.getUsername(), 
                linkActivacion
            );
        }

        // 5. Crear Agencia Matriz por defecto para que la nueva cooperativa pueda crear cajas/ventanillas de inmediato
        jdbcTemplate.update(
            "INSERT INTO agencias (codigo, nombre, direccion, estado, empresa_id, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            "001",
            "Agencia Matriz - " + savedEmpresa.getRazonSocial(),
            savedEmpresa.getDireccion() != null && !savedEmpresa.getDireccion().trim().isEmpty() 
                ? savedEmpresa.getDireccion() 
                : "Dirección Principal",
            "ACTIVA",
            savedEmpresa.getId()
        );

        // 6. Clonar Plan de Cuentas, Productos contables y enlaces desde el Tenant Plantilla (ID = 1)
        clonarDesdeTenantPlantilla(1, savedEmpresa.getId());
    }

    private void clonarDesdeTenantPlantilla(Integer templateTenantId, Integer newEmpresaId) {
        // 1. Clonar plan_cuentas
        java.util.List<java.util.Map<String, Object>> oldCuentas = jdbcTemplate.queryForList(
            "SELECT id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento, estado FROM plan_cuentas WHERE empresa_id = ? " +
            "AND (codigo_contable NOT LIKE '1.1.01.%' OR codigo_contable IN ('1.1.01.01', '1.1.01.05'))",
            templateTenantId
        );
        java.util.Map<Integer, Integer> planCuentasMap = new java.util.HashMap<>();
        for (java.util.Map<String, Object> cuenta : oldCuentas) {
            Integer oldId = ((Number) cuenta.get("id")).intValue();
            String codigo = (String) cuenta.get("codigo_contable");
            String nombre = (String) cuenta.get("nombre_cuenta");
            String tipo = (String) cuenta.get("tipo_cuenta");
            Boolean esMovimiento = (Boolean) cuenta.get("es_movimiento");
            String estado = (String) cuenta.get("estado");

            Integer newId = jdbcTemplate.queryForObject(
                "INSERT INTO plan_cuentas (empresa_id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id",
                Integer.class,
                newEmpresaId, codigo, nombre, tipo, esMovimiento, estado
            );
            planCuentasMap.put(oldId, newId);
        }

        // 2. Clonar productos_ahorro
        java.util.List<java.util.Map<String, Object>> oldProdAhorro = jdbcTemplate.queryForList(
            "SELECT id, nombre, tipo_producto, tasa_interes_anual, monto_minimo_apertura, saldo_minimo_requerido, tipo_retiro, tasa_penalizacion_retiro, cuenta_contable_pasivo_id, cuenta_contable_gasto_id, estado FROM productos_ahorro WHERE empresa_id = ?",
            templateTenantId
        );
        for (java.util.Map<String, Object> prod : oldProdAhorro) {
            String nombre = (String) prod.get("nombre");
            String tipoProd = (String) prod.get("tipo_producto");
            java.math.BigDecimal tasaInteres = (java.math.BigDecimal) prod.get("tasa_interes_anual");
            java.math.BigDecimal montoMin = (java.math.BigDecimal) prod.get("monto_minimo_apertura");
            java.math.BigDecimal saldoMin = (java.math.BigDecimal) prod.get("saldo_minimo_requerido");
            String tipoRetiro = (String) prod.get("tipo_retiro");
            java.math.BigDecimal tasaPenalizacion = (java.math.BigDecimal) prod.get("tasa_penalizacion_retiro");
            Integer oldPasivoId = ((Number) prod.get("cuenta_contable_pasivo_id")).intValue();
            Integer oldGastoId = ((Number) prod.get("cuenta_contable_gasto_id")).intValue();
            String estado = (String) prod.get("estado");

            Integer newPasivoId = planCuentasMap.get(oldPasivoId);
            Integer newGastoId = planCuentasMap.get(oldGastoId);

            jdbcTemplate.update(
                "INSERT INTO productos_ahorro (empresa_id, nombre, tipo_producto, tasa_interes_anual, monto_minimo_apertura, saldo_minimo_requerido, tipo_retiro, tasa_penalizacion_retiro, cuenta_contable_pasivo_id, cuenta_contable_gasto_id, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                newEmpresaId, nombre, tipoProd, tasaInteres, montoMin, saldoMin, tipoRetiro, tasaPenalizacion, newPasivoId, newGastoId, estado
            );
        }

        // 3. Clonar productos_credito
        java.util.List<java.util.Map<String, Object>> oldProdCredito = jdbcTemplate.queryForList(
            "SELECT id, nombre, monto_minimo, monto_maximo, plazo_minimo_meses, plazo_maximo_meses, tasa_interes_anual, tasa_mora_anual, porcentaje_seguro_desgravamen, cuenta_contable_cartera_id, cuenta_contable_ingresos_intereses_id, cuenta_contable_mora_id, cuenta_contable_seguro_id, estado FROM productos_credito WHERE empresa_id = ?",
            templateTenantId
        );
        for (java.util.Map<String, Object> prod : oldProdCredito) {
            String nombre = (String) prod.get("nombre");
            java.math.BigDecimal montoMin = (java.math.BigDecimal) prod.get("monto_minimo");
            java.math.BigDecimal montoMax = (java.math.BigDecimal) prod.get("monto_maximo");
            Integer plazoMin = ((Number) prod.get("plazo_minimo_meses")).intValue();
            Integer plazoMax = ((Number) prod.get("plazo_maximo_meses")).intValue();
            java.math.BigDecimal tasaInteres = (java.math.BigDecimal) prod.get("tasa_interes_anual");
            java.math.BigDecimal tasaMora = (java.math.BigDecimal) prod.get("tasa_mora_anual");
            java.math.BigDecimal pctSeguro = (java.math.BigDecimal) prod.get("porcentaje_seguro_desgravamen");
            Integer oldCarteraId = ((Number) prod.get("cuenta_contable_cartera_id")).intValue();
            Integer oldIngresosId = ((Number) prod.get("cuenta_contable_ingresos_intereses_id")).intValue();
            Integer oldMoraId = ((Number) prod.get("cuenta_contable_mora_id")).intValue();
            Number oldSeguroNum = (Number) prod.get("cuenta_contable_seguro_id");
            Integer oldSeguroId = oldSeguroNum != null ? oldSeguroNum.intValue() : null;
            String estado = (String) prod.get("estado");

            Integer newCarteraId = planCuentasMap.get(oldCarteraId);
            Integer newIngresosId = planCuentasMap.get(oldIngresosId);
            Integer newMoraId = planCuentasMap.get(oldMoraId);
            Integer newSeguroId = oldSeguroId != null ? planCuentasMap.get(oldSeguroId) : null;

            jdbcTemplate.update(
                "INSERT INTO productos_credito (empresa_id, nombre, monto_minimo, monto_maximo, plazo_minimo_meses, plazo_maximo_meses, tasa_interes_anual, tasa_mora_anual, porcentaje_seguro_desgravamen, cuenta_contable_cartera_id, cuenta_contable_ingresos_intereses_id, cuenta_contable_mora_id, cuenta_contable_seguro_id, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                newEmpresaId, nombre, montoMin, montoMax, plazoMin, plazoMax, tasaInteres, tasaMora, pctSeguro, newCarteraId, newIngresosId, newMoraId, newSeguroId, estado
            );
        }

        // 4. Copiar enlaces contables de la empresa plantilla
        java.util.Map<String, Object> templateEmpresa = jdbcTemplate.queryForMap(
            "SELECT cuenta_contable_caja_id, cuenta_contable_cartera_id, cuenta_contable_obligaciones_id, " +
            "cuenta_contable_aportaciones_id, cuenta_contable_seguro_id, cuenta_contable_papeleria_id, " +
            "cuenta_contable_gastos_intereses_id, cuenta_contable_ingresos_intereses_id, cuenta_contable_mora_id " +
            "FROM empresas WHERE id = ?",
            templateTenantId
        );

        jdbcTemplate.update(
            "UPDATE empresas SET cuenta_contable_caja_id = ?, cuenta_contable_cartera_id = ?, " +
            "cuenta_contable_obligaciones_id = ?, cuenta_contable_aportaciones_id = ?, " +
            "cuenta_contable_seguro_id = ?, cuenta_contable_papeleria_id = ?, " +
            "cuenta_contable_gastos_intereses_id = ?, cuenta_contable_ingresos_intereses_id = ?, " +
            "cuenta_contable_mora_id = ? WHERE id = ?",
            resolveMapId(templateEmpresa.get("cuenta_contable_caja_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_cartera_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_obligaciones_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_aportaciones_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_seguro_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_papeleria_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_gastos_intereses_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_ingresos_intereses_id"), planCuentasMap),
            resolveMapId(templateEmpresa.get("cuenta_contable_mora_id"), planCuentasMap),
            newEmpresaId
        );
    }

    private Integer resolveMapId(Object oldIdObj, java.util.Map<Integer, Integer> map) {
        if (oldIdObj == null) return null;
        Integer oldId = ((Number) oldIdObj).intValue();
        return map.get(oldId);
    }

    private String hashSha256(String base) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
