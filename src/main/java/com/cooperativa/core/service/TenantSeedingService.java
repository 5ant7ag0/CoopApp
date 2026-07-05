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
            String linkActivacion = "http://localhost:5173/recuperar-clave?token=" + tokenRaw + "&identificacion=" + admin.getUsername();
            notificacionService.enviarCredencialesSaaS(
                savedEmpresa.getCorreoGerente(), 
                savedEmpresa.getRazonSocial(), 
                admin.getUsername(), 
                linkActivacion
            );
        }

        // 5. Inyectar el Plan de Cuentas base SEPS y configurar los enlaces contables
        sembrarPlanCuentasDefecto(savedEmpresa.getId());
    }

    private void sembrarPlanCuentasDefecto(Integer empresaId) {
        Integer cajaId = null;
        Integer carteraId = null;
        Integer obligacionesId = null;
        Integer aportacionesId = null;
        Integer seguroId = null;
        Integer gastosInteresesId = null;
        Integer ingresosInteresesId = null;
        Integer moraId = null;

        String[][] cuentasDefecto = {
            {"1", "ACTIVOS", "ACTIVO", "false"},
            {"1.1", "FONDOS DISPONIBLES", "ACTIVO", "false"},
            {"1.1.01", "CAJA", "ACTIVO", "false"},
            {"1.1.01.05", "Caja General Ventanilla", "ACTIVO", "true"},
            {"1.4", "CARTERA DE CREDITOS", "ACTIVO", "false"},
            {"1.4.01", "Cartera de Créditos por Desembolsar", "ACTIVO", "true"},
            {"1.4.02", "Cartera de Créditos Vigente", "ACTIVO", "true"},
            
            {"2", "PASIVOS", "PASIVO", "false"},
            {"2.1", "OBLIGACIONES CON EL PUBLICO", "PASIVO", "false"},
            {"2.1.01", "DEPOSITOS A LA VISTA", "PASIVO", "false"},
            {"2.1.01.05", "Cuentas de Ahorros de Socios", "PASIVO", "true"},
            
            {"3", "PATRIMONIO", "PATRIMONIO", "false"},
            {"3.1", "CAPITAL SOCIAL", "PATRIMONIO", "false"},
            {"3.1.01", "Capital Social Numerario", "PATRIMONIO", "false"},
            {"3.1.01.05", "Aportaciones Obligatorias de Socios", "PATRIMONIO", "true"},
            
            {"4", "GASTOS", "GASTO", "false"},
            {"4.1", "GASTOS FINANCIEROS", "GASTO", "false"},
            {"4.1.01", "Gastos por Intereses de Depósitos", "GASTO", "true"},
            {"4.1.02", "Gastos por Seguros y Papelería", "GASTO", "true"},
            
            {"5", "INGRESOS", "INGRESO", "false"},
            {"5.1", "INGRESOS FINANCIEROS", "INGRESO", "false"},
            {"5.1.01", "INGRESOS POR INTERESES DE CARTERA", "INGRESO", "false"},
            {"5.1.01.05", "Intereses Cartera de Créditos Vigente", "INGRESO", "true"},
            {"5.1.02", "INGRESOS POR INTERESES DE MORA", "INGRESO", "false"},
            {"5.1.02.05", "Intereses por Mora de Cartera", "INGRESO", "true"}
        };

        for (String[] def : cuentasDefecto) {
            Integer cuentaId = jdbcTemplate.queryForObject(
                "INSERT INTO plan_cuentas (empresa_id, codigo_contable, nombre_cuenta, tipo_cuenta, es_movimiento, estado, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) RETURNING id",
                Integer.class,
                empresaId, def[0], def[1], def[2], Boolean.parseBoolean(def[3]), "ACTIVO"
            );
            String codigo = def[0];

            if ("1.1.01.05".equals(codigo)) cajaId = cuentaId;
            else if ("1.4.01".equals(codigo)) carteraId = cuentaId;
            else if ("2.1.01.05".equals(codigo)) obligacionesId = cuentaId;
            else if ("3.1.01.05".equals(codigo)) aportacionesId = cuentaId;
            else if ("4.1.02".equals(codigo)) seguroId = cuentaId;
            else if ("4.1.01".equals(codigo)) gastosInteresesId = cuentaId;
            else if ("5.1.01.05".equals(codigo)) ingresosInteresesId = cuentaId;
            else if ("5.1.02.05".equals(codigo)) moraId = cuentaId;
        }

        // Vincular las cuentas creadas con la configuración funcional contable de la empresa
        jdbcTemplate.update(
            "UPDATE empresas SET cuenta_contable_caja_id = ?, cuenta_contable_cartera_id = ?, " +
            "cuenta_contable_obligaciones_id = ?, cuenta_contable_aportaciones_id = ?, " +
            "cuenta_contable_seguro_id = ?, cuenta_contable_papeleria_id = ?, " +
            "cuenta_contable_gastos_intereses_id = ?, cuenta_contable_ingresos_intereses_id = ?, " +
            "cuenta_contable_mora_id = ? WHERE id = ?",
            cajaId, carteraId, obligacionesId, aportacionesId, seguroId, seguroId,
            gastosInteresesId, ingresosInteresesId, moraId, empresaId
        );
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
