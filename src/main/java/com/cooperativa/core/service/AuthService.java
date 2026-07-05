package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.LoginResponseDTO;
import com.cooperativa.core.dto.UserProfileResponseDTO;
import com.cooperativa.core.model.ControlSesiones;
import com.cooperativa.core.model.Socio;
import com.cooperativa.core.model.SociosCredenciales;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.ControlSesionesRepository;
import com.cooperativa.core.repository.SocioRepository;
import com.cooperativa.core.repository.SociosCredencialesRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import com.cooperativa.core.security.EncryptionService;
import com.cooperativa.core.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.cooperativa.core.model.TokensRecuperacion;
import com.cooperativa.core.model.LogsAuditoria;
import com.cooperativa.core.repository.TokensRecuperacionRepository;
import com.cooperativa.core.service.NotificacionService;
import com.cooperativa.core.service.LogsAuditoriaService;

/**
 * Servicio centralizado para gestionar la autenticacion de administradores y socios,
 * el registro de credenciales digitales y el control inmutable de sesiones.
 */
@Service
public class AuthService {

    @Autowired
    private UsuarioAdminRepository adminRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private SociosCredencialesRepository credencialesRepository;

    @Autowired
    private ControlSesionesRepository sesionesRepository;

    @Autowired
    private EncryptionService encryptionService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private TokensRecuperacionRepository tokensRecuperacionRepository;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /**
     * Autenticacion de personal administrativo (Backoffice).
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO loginAdmin(String username, String password, String ip, String userAgent) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede iniciar sesion sin especificar la institucion (X-Tenant-ID).");
        }

        // Buscar usuario en la base del inquilino
        UsuariosAdmin admin = adminRepository.findByUsernameAndEmpresaId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas para el usuario administrativo."));

        if (!"ACTIVO".equals(admin.getEstado())) {
            throw new IllegalStateException("El usuario administrativo no se encuentra activo.");
        }

        // Validar contraseña
        if (!encryptionService.checkPassword(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciales invalidas para el usuario administrativo.");
        }

        // Generar JWT
        String token = jwtUtil.generateToken(admin.getUsername(), admin.getRol(), tenantId);
        String tokenHash = hashSha256(token);

        // Guardar la sesion en BDD para trazabilidad contable
        ControlSesiones sesion = new ControlSesiones();
        sesion.setEmpresaId(tenantId);
        sesion.setUsuarioAdminId(admin.getId());
        sesion.setTokenJwtHash(tokenHash);
        sesion.setFechaInicio(LocalDateTime.now());
        sesion.setFechaExpiracion(LocalDateTime.now().plusHours(8));
        sesion.setUltimaActividad(LocalDateTime.now());
        sesion.setDireccionIp(ip);
        sesion.setDispositivoInfo(userAgent);
        sesion.setEstado("ACTIVA");
        sesionesRepository.save(sesion);

        // Registrar fecha de ultimo acceso
        admin.setUltimoAcceso(LocalDateTime.now());
        adminRepository.save(admin);

        return new LoginResponseDTO(token, admin.getUsername(), admin.getNombresCompletos(), admin.getRol(), tenantId, admin.isCambiarPasswordProximoInicio());
    }

    /**
     * Autenticacion de socios para canales digitales (App Movil / Web Socio).
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalArgumentException.class)
    public LoginResponseDTO loginSocio(String identificacion, String password, String ip, String userAgent) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede iniciar sesion sin especificar la institucion (X-Tenant-ID).");
        }

        // Buscar credenciales del socio por identificacion y empresaId
        SociosCredenciales creds = credencialesRepository.findByIdentificacionAndEmpresaId(identificacion, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas para el socio."));

        Socio socio = creds.getSocio();
        if (!"ACTIVO".equals(socio.getEstado())) {
            throw new IllegalStateException("La cuenta de socio no se encuentra activa o no ha sido aprobada.");
        }

        if (!"ACTIVO".equals(creds.getEstadoAcceso())) {
            throw new IllegalStateException("El acceso digital del socio esta temporalmente inactivo o bloqueado.");
        }

        // Validar contrasena
        if (!encryptionService.checkPassword(password, creds.getPasswordHash())) {
            // Incrementar intentos fallidos
            creds.setIntentosFallidos(creds.getIntentosFallidos() + 1);
            if (creds.getIntentosFallidos() >= 5) {
                creds.setEstadoAcceso("BLOQUEADA");
                creds.setBloqueadoHasta(null);

                // Registro de Auditoria para el bloqueo
                LogsAuditoria auditLog = new LogsAuditoria();
                auditLog.setSocio(socio);
                auditLog.setAccion("BLOQUEO_ACCESO_FALLIDO");
                auditLog.setTablaAfectada("socios_credenciales");
                auditLog.setRegistroId(creds.getId());
                auditLog.setDireccionIp(ip != null ? ip : "127.0.0.1");
                auditLog.setDispositivoInfo(userAgent != null ? userAgent : "Desconocido");
                auditLog.setValorAnterior(Map.of("estadoAcceso", "ACTIVO"));
                auditLog.setValorNuevo(Map.of("estadoAcceso", "BLOQUEADA"));
                logsAuditoriaService.registrarLog(auditLog);
            }
            credencialesRepository.save(creds);
            throw new IllegalArgumentException("Credenciales invalidas para el socio.");
        }

        // Resetear intentos fallidos tras login exitoso
        creds.setIntentosFallidos(0);
        credencialesRepository.save(creds);

        // Generar JWT
        String token = jwtUtil.generateToken(socio.getIdentificacion(), "SOCIO", tenantId);
        String tokenHash = hashSha256(token);

        // Guardar la sesion
        ControlSesiones sesion = new ControlSesiones();
        sesion.setEmpresaId(tenantId);
        sesion.setSocio(socio);
        sesion.setTokenJwtHash(tokenHash);
        sesion.setFechaInicio(LocalDateTime.now());
        sesion.setFechaExpiracion(LocalDateTime.now().plusHours(8));
        sesion.setUltimaActividad(LocalDateTime.now());
        sesion.setDireccionIp(ip);
        sesion.setDispositivoInfo(userAgent);
        sesion.setEstado("ACTIVA");
        sesionesRepository.save(sesion);

        return new LoginResponseDTO(token, socio.getIdentificacion(), socio.getNombresCompletos(), "SOCIO", tenantId);
    }

    /**
     * Registro de credenciales digitales para un socio existente en el Core.
     */
    @Transactional(rollbackFor = Exception.class)
    public void registrarCredencialesSocio(String identificacion, String password) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede registrar credenciales sin especificar la institucion (X-Tenant-ID).");
        }

        // Buscar al socio en el Core de la empresa
        Socio socio = socioRepository.findByIdentificacionAndEmpresaId(identificacion, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: No existe un socio registrado en esta cooperativa con la identificacion provista."));

        // Validar que no tenga ya credenciales asignadas
        Optional<SociosCredenciales> credsExistentes = credencialesRepository.findBySocioId(socio.getId());
        if (credsExistentes.isPresent()) {
            throw new IllegalStateException("El socio ya cuenta con credenciales de acceso digital configuradas.");
        }

        // Crear credenciales seguras
        SociosCredenciales nuevasCreds = new SociosCredenciales();
        nuevasCreds.setSocio(socio);
        nuevasCreds.setPasswordHash(encryptionService.hashPassword(password));
        nuevasCreds.setEstadoAcceso("ACTIVO");
        nuevasCreds.setIntentosFallidos(0);
        credencialesRepository.save(nuevasCreds);
    }

    /**
     * Cierre de sesion (Inactivacion del Token).
     */
    @Transactional(rollbackFor = Exception.class)
    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        String tokenHash = hashSha256(token);
        sesionesRepository.findByTokenJwtHash(tokenHash).ifPresent(sesion -> {
            sesion.setEstado("CERRADA");
            sesionesRepository.save(sesion);
        });
    }

    /**
     * Obtiene el perfil del usuario autenticado en base al username y rol del JWT.
     */
    public UserProfileResponseDTO obtenerPerfil(String username, String rol, Integer empresaId) {
        if ("SOCIO".equals(rol)) {
            Socio socio = socioRepository.findByIdentificacionAndEmpresaId(username, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Socio no encontrado para el usuario autenticado."));
            return new UserProfileResponseDTO(socio.getIdentificacion(), socio.getNombresCompletos(), "SOCIO", empresaId, socio);
        } else {
            UsuariosAdmin admin = adminRepository.findByUsernameAndEmpresaId(username, empresaId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario administrativo no encontrado."));
            return new UserProfileResponseDTO(admin.getUsername(), admin.getNombresCompletos(), admin.getRol(), empresaId, admin);
        }
    }

    /**
     * Helper para hashear el token JWT a SHA-256 de manera nativa e inmutable.
     */
    private String hashSha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error al generar hash de seguridad para la sesion: ", ex);
        }
    }

    /**
     * Cambio de contraseña digital del socio validando que la contraseña actual sea correcta.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cambiarClaveSocio(String identificacion, String claveActual, String claveNueva) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede cambiar la clave sin especificar la institucion (X-Tenant-ID).");
        }

        SociosCredenciales creds = credencialesRepository.findByIdentificacionAndEmpresaId(identificacion, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: No se encontraron credenciales digitales para el socio."));

        // Validar contraseña actual
        if (!encryptionService.checkPassword(claveActual, creds.getPasswordHash())) {
            throw new IllegalArgumentException("Error: La contraseña actual ingresada es incorrecta.");
        }

        // Validar que la nueva contraseña no esté vacía
        if (claveNueva == null || claveNueva.trim().isEmpty()) {
            throw new IllegalArgumentException("Error: La nueva contraseña no puede estar vacía.");
        }

        // Hashear y guardar
        creds.setPasswordHash(encryptionService.hashPassword(claveNueva));
        credencialesRepository.save(creds);
    }

    /**
     * Solicita la recuperación de contraseña digital del socio.
     * Genera un token (UUID para CORREO, OTP de 6 dígitos para SMS), lo persiste hasheado (SHA-256)
     * y simula el envío a través del NotificacionService.
     */
    @Transactional(rollbackFor = Exception.class)
    public String solicitarRecuperacion(String identificacion, String canal, String ip, String userAgent) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede solicitar recuperacion sin especificar la institucion (X-Tenant-ID).");
        }

        if (identificacion == null || identificacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La identificacion del socio es requerida.");
        }
        if (canal == null || canal.trim().isEmpty()) {
            throw new IllegalArgumentException("El canal es requerido.");
        }

        // 1. Buscar socio
        Socio socio = socioRepository.findByIdentificacionAndEmpresaId(identificacion, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Socio no encontrado con la identificacion provista."));

        // 2. Buscar credenciales
        SociosCredenciales creds = credencialesRepository.findBySocioId(socio.getId())
                .orElseThrow(() -> new IllegalArgumentException("No existen credenciales registradas para este socio."));

        // 3. Generar Token/OTP
        String tokenRaw;
        if ("CORREO".equals(canal) || "SMS".equals(canal)) {
            java.security.SecureRandom random = new java.security.SecureRandom();
            int otpNum = 100000 + random.nextInt(900000);
            tokenRaw = String.valueOf(otpNum);
        } else {
            throw new IllegalArgumentException("Canal no soportado: " + canal);
        }

        // Hash SHA-256
        String tokenHash = hashSha256(tokenRaw);

        // 4. Guardar en Base de Datos
        TokensRecuperacion tokenRec = new TokensRecuperacion();
        tokenRec.setSocio(socio);
        tokenRec.setTokenHash(tokenHash);
        tokenRec.setCanal(canal);
        tokenRec.setFechaExpiracion(LocalDateTime.now().plusMinutes(15));
        tokenRec.setUtilizado(false);
        tokenRec.setIntentosFallidos(0);
        tokensRecuperacionRepository.save(tokenRec);

        // 5. Enviar notificacion (simulacion)
        if ("CORREO".equals(canal)) {
            notificacionService.enviarRecuperacionCorreo(socio, tokenRaw);
        } else {
            notificacionService.enviarRecuperacionSms(socio, tokenRaw);
        }

        // 6. Registro de Auditoria
        LogsAuditoria auditLog = new LogsAuditoria();
        auditLog.setSocio(socio);
        auditLog.setAccion("SOLICITAR_RECUPERACION_" + canal);
        auditLog.setTablaAfectada("tokens_recuperacion");
        auditLog.setRegistroId(tokenRec.getId());
        auditLog.setDireccionIp(ip != null ? ip : "127.0.0.1");
        auditLog.setDispositivoInfo(userAgent != null ? userAgent : "Desconocido");
        auditLog.setValorAnterior(Map.of("solicitud", "nueva"));
        auditLog.setValorNuevo(Map.of(
            "canal", canal,
            "socioId", socio.getId(),
            "tokenHash", tokenHash,
            "expira", tokenRec.getFechaExpiracion().toString()
        ));
        logsAuditoriaService.registrarLog(auditLog);

        return enmascararCorreo(socio.getCorreo());
    }

    private String enmascararCorreo(String correo) {
        if (correo == null || !correo.contains("@")) return correo;
        String[] parts = correo.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name + "***@" + domain;
        }
        return name.substring(0, 2) + "***@" + domain;
    }

    /**
     * Flujo Asistido (Backoffice): Genera enlace de restablecimiento e invalida credencial actual.
     */
    @Transactional(rollbackFor = Exception.class)
    public String enviarEnlaceRestablecimiento(Integer socioId, String ip, String userAgent) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede generar enlace sin especificar la institucion.");
        }

        Socio socio = socioRepository.findById(socioId)
                .filter(s -> s.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Socio no encontrado."));

        // 1. Invalidar credenciales actuales si existen
        credencialesRepository.findBySocioId(socio.getId()).ifPresent(creds -> {
            creds.setEstadoAcceso("REQUIERE_CAMBIO");
            credencialesRepository.save(creds);
        });

        // 2. Generar Token
        String tokenRaw = UUID.randomUUID().toString();
        String tokenHash = hashSha256(tokenRaw);

        // 3. Guardar en Base de Datos
        TokensRecuperacion tokenRec = new TokensRecuperacion();
        tokenRec.setSocio(socio);
        tokenRec.setTokenHash(tokenHash);
        tokenRec.setCanal("CORREO");
        tokenRec.setFechaExpiracion(LocalDateTime.now().plusMinutes(15));
        tokenRec.setUtilizado(false);
        tokenRec.setIntentosFallidos(0);
        tokensRecuperacionRepository.save(tokenRec);

        // 4. Enviar notificación
        notificacionService.enviarRecuperacionCorreo(socio, tokenRaw);

        // 5. Registro de Auditoria
        LogsAuditoria auditLog = new LogsAuditoria();
        auditLog.setSocio(socio);
        auditLog.setAccion("ENVIAR_ENLACE_RESTABLECIMIENTO_ASISTIDO");
        auditLog.setTablaAfectada("tokens_recuperacion");
        auditLog.setRegistroId(tokenRec.getId());
        auditLog.setDireccionIp(ip != null ? ip : "127.0.0.1");
        auditLog.setDispositivoInfo(userAgent != null ? userAgent : "Backoffice");
        auditLog.setValorAnterior(Map.of("solicitud", "asistida"));
        auditLog.setValorNuevo(Map.of("tokenHash", tokenHash, "expira", tokenRec.getFechaExpiracion().toString()));
        logsAuditoriaService.registrarLog(auditLog);

        return enmascararCorreo(socio.getCorreo());
    }

    /**
     * Valida el token o código OTP sin consumirlo ni cambiar la clave.
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalArgumentException.class)
    public void validarToken(String identificacion, String token, String ip, String userAgent) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("El token o codigo OTP es requerido.");
        }
        if (identificacion == null || identificacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La identificacion o usuario es requerido.");
        }

        String tokenHash = hashSha256(token);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, empresa_id, usuario_admin_id, socio_id, intentos_fallidos FROM tokens_recuperacion WHERE token_hash = ? AND utilizado = false",
            tokenHash
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Código OTP inválido o expirado.");
        }

        Map<String, Object> tokenRow = rows.get(0);
        Integer resolvedTenantId = (Integer) tokenRow.get("empresa_id");
        Integer intentosFallidos = (Integer) tokenRow.get("intentos_fallidos");
        Long tokenId = ((Number) tokenRow.get("id")).longValue();

        TenantContext.setCurrentTenant(resolvedTenantId);

        if (intentosFallidos >= 3) {
            jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);
            throw new IllegalArgumentException("El código OTP ha sido invalidado por exceso de intentos.");
        }

        // Validar si el usuario/socio coincide
        boolean matches = false;
        Number socioId = (Number) tokenRow.get("socio_id");
        Number adminId = (Number) tokenRow.get("usuario_admin_id");

        if (socioId != null) {
            Optional<Socio> socioOpt = socioRepository.findByIdRaw(socioId.intValue());
            if (socioOpt.isPresent() && identificacion.equals(socioOpt.get().getIdentificacion())) {
                matches = true;
            }
        } else if (adminId != null) {
            Optional<UsuariosAdmin> adminOpt = adminRepository.findByIdRaw(adminId.intValue());
            if (adminOpt.isPresent() && (identificacion.equals(adminOpt.get().getUsername()) || identificacion.equals(adminOpt.get().getIdentificacion()))) {
                matches = true;
            }
        }

        if (!matches) {
            // Incrementar intentos fallidos
            jdbcTemplate.update("UPDATE tokens_recuperacion SET intentos_fallidos = intentos_fallidos + 1 WHERE id = ?", tokenId);
            int nuevosIntentos = intentosFallidos + 1;
            if (nuevosIntentos >= 3) {
                jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);
                throw new IllegalArgumentException("El código OTP ha sido invalidado por exceso de intentos.");
            }
            throw new IllegalArgumentException("Identificación o usuario no coincide con el código OTP.");
        }
    }

    /**
     * Valida el token o código OTP de recuperación e inyecta la nueva clave digital.
     * Mitiga fuerza bruta invalidando el token tras 3 intentos fallidos consecutivos de validación.
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalArgumentException.class)
    public void validarYRestablecerClave(String identificacion, String token, String passwordNueva, String ip, String userAgent) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("El token o codigo OTP es requerido.");
        }
        if (identificacion == null || identificacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La identificacion o usuario es requerido.");
        }
        if (passwordNueva == null || passwordNueva.trim().isEmpty()) {
            throw new IllegalArgumentException("La nueva contrasena es requerida.");
        }

        String tokenHash = hashSha256(token);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, empresa_id, usuario_admin_id, socio_id, intentos_fallidos, fecha_expiracion FROM tokens_recuperacion WHERE token_hash = ? AND utilizado = false",
            tokenHash
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Token o enlace de restablecimiento invalido o expirado.");
        }

        Map<String, Object> tokenRow = rows.get(0);
        Integer resolvedTenantId = (Integer) tokenRow.get("empresa_id");
        Integer intentosFallidos = (Integer) tokenRow.get("intentos_fallidos");
        Long tokenId = ((Number) tokenRow.get("id")).longValue();
        LocalDateTime fechaExpiracion = ((java.sql.Timestamp) tokenRow.get("fecha_expiracion")).toLocalDateTime();

        TenantContext.setCurrentTenant(resolvedTenantId);

        if (fechaExpiracion.isBefore(LocalDateTime.now())) {
            jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);
            throw new IllegalArgumentException("Token o enlace de restablecimiento invalido o expirado.");
        }

        if (intentosFallidos >= 3) {
            jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);
            throw new IllegalArgumentException("El enlace de restablecimiento ha sido invalidado por exceso de intentos fallidos.");
        }

        // Intentar buscar como Socio primero (utilizando raw query para saltar filtro de TenantId de Hibernate)
        Optional<Socio> socioOpt = socioRepository.findByIdentificacionAndEmpresaIdRaw(identificacion, resolvedTenantId);

        if (socioOpt.isPresent()) {
            Socio socio = socioOpt.get();
            Number socioId = (Number) tokenRow.get("socio_id");
            if (socioId == null || socioId.intValue() != socio.getId()) {
                // Incrementar intentos fallidos
                jdbcTemplate.update("UPDATE tokens_recuperacion SET intentos_fallidos = intentos_fallidos + 1 WHERE id = ?", tokenId);
                int nuevosIntentos = intentosFallidos + 1;
                if (nuevosIntentos >= 3) {
                    jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);
                }
                throw new IllegalArgumentException("Token o codigo OTP invalido.");
            }

            boolean existsCreds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM socios_credenciales WHERE socio_id = ?",
                Integer.class,
                socio.getId()
            ) > 0;

            String passHash = encryptionService.hashPassword(passwordNueva);
            if (existsCreds) {
                jdbcTemplate.update(
                    "UPDATE socios_credenciales SET password_hash = ?, estado_acceso = 'ACTIVO', intentos_fallidos = 0, bloqueado_hasta = NULL WHERE socio_id = ?",
                    passHash, socio.getId()
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO socios_credenciales (socio_id, password_hash, estado_acceso, intentos_fallidos, empresa_id) VALUES (?, ?, 'ACTIVO', 0, ?)",
                    socio.getId(), passHash, resolvedTenantId
                );
            }

            jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);

            String valorAnteriorJson = "{\"estadoAcceso\":\"BLOQUEADA\"}";
            String valorNuevoJson = "{\"estadoAcceso\":\"ACTIVO\"}";
            jdbcTemplate.update(
                "INSERT INTO logs_auditoria (socio_id, accion, tabla_afectada, registro_id, valor_anterior, valor_nuevo, fecha, direccion_ip, dispositivo_info, empresa_id) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP, ?, ?, ?)",
                socio.getId(),
                "RESTABLECER_CLAVE_EXITOSO",
                "socios_credenciales",
                socio.getId(),
                valorAnteriorJson,
                valorNuevoJson,
                ip != null ? ip : "127.0.0.1",
                userAgent != null ? userAgent : "Desconocido",
                resolvedTenantId
            );

        } else {
            // Intentar buscar como UsuarioAdmin (Gerente u otro)
            UsuariosAdmin admin = adminRepository.findByUsernameOrIdentificacionRaw(identificacion, resolvedTenantId)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con la identificacion o nombre de usuario provisto."));

            Number adminId = (Number) tokenRow.get("usuario_admin_id");
            if (adminId == null || adminId.intValue() != admin.getId()) {
                // Incrementar intentos fallidos
                jdbcTemplate.update("UPDATE tokens_recuperacion SET intentos_fallidos = intentos_fallidos + 1 WHERE id = ?", tokenId);
                int nuevosIntentos = intentosFallidos + 1;
                if (nuevosIntentos >= 3) {
                    jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);
                }
                throw new IllegalArgumentException("Token o enlace de restablecimiento invalido.");
            }

            String newPasswordHash = encryptionService.hashPassword(passwordNueva);
            jdbcTemplate.update(
                "UPDATE usuarios_admin SET password_hash = ?, cambiar_password_proximo_inicio = ?, estado = ? WHERE id = ?",
                newPasswordHash, false, "ACTIVO", admin.getId()
            );

            jdbcTemplate.update("UPDATE tokens_recuperacion SET utilizado = true WHERE id = ?", tokenId);

            String valorAnteriorJson = String.format("{\"username\":\"%s\"}", admin.getUsername());
            String valorNuevoJson = "{\"restablecimiento\":\"exitoso\"}";
            jdbcTemplate.update(
                "INSERT INTO logs_auditoria (usuario_admin_id, accion, tabla_afectada, registro_id, valor_anterior, valor_nuevo, fecha, direccion_ip, dispositivo_info, empresa_id) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP, ?, ?, ?)",
                admin.getId(),
                "RESTABLECER_CLAVE_ADMIN_EXITOSO",
                "usuarios_admin",
                admin.getId(),
                valorAnteriorJson,
                valorNuevoJson,
                ip != null ? ip : "127.0.0.1",
                userAgent != null ? userAgent : "Desconocido",
                resolvedTenantId
            );
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String enviarEnlaceRestablecimientoAdmin(Integer tenantId, String ip, String userAgent) {
        // Find Gerente General
        UsuariosAdmin gerente;
        Optional<UsuariosAdmin> gerenteOpt = adminRepository.findGerenteGeneralRaw(tenantId);
        if (gerenteOpt.isPresent()) {
            gerente = gerenteOpt.get();
        } else {
            // Self-healing: Re-create the missing admin from the Empresa details!
            List<Map<String, Object>> empresaRows = jdbcTemplate.queryForList(
                "SELECT razon_social, correo_gerente, cedula_representante, representante_legal FROM empresas WHERE id = ?",
                tenantId
            );
            if (empresaRows.isEmpty()) {
                throw new IllegalArgumentException("No se encontró la cooperativa especificada.");
            }
            Map<String, Object> empRow = empresaRows.get(0);
            String correoGerente = (String) empRow.get("correo_gerente");
            String cedulaRepresentante = (String) empRow.get("cedula_representante");
            String representanteLegal = (String) empRow.get("representante_legal");

            String defaultUsername = "coopro";
            String passwordHash = encryptionService.hashPassword(UUID.randomUUID().toString());

            Integer adminId = jdbcTemplate.queryForObject(
                "INSERT INTO usuarios_admin (empresa_id, username, password_hash, nombres_completos, correo, rol, estado, identificacion, cambiar_password_proximo_inicio, limite_transaccion_max, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 'GERENTE_GENERAL', 'ACTIVO', ?, true, 0.00, CURRENT_TIMESTAMP) RETURNING id",
                Integer.class,
                tenantId, defaultUsername, passwordHash,
                representanteLegal, correoGerente, cedulaRepresentante
            );

            gerente = new UsuariosAdmin();
            gerente.setId(adminId);
            gerente.setUsername(defaultUsername);
            gerente.setCorreo(correoGerente);
            gerente.setNombresCompletos(representanteLegal);
            gerente.setIdentificacion(cedulaRepresentante);
        }

        // Invalidar contraseña actual del gerente inmediatamente por seguridad
        String newPassHash = UUID.randomUUID().toString();
        jdbcTemplate.update(
            "UPDATE usuarios_admin SET password_hash = ? WHERE id = ?",
            newPassHash, gerente.getId()
        );

        // Generate Token
        String tokenRaw = UUID.randomUUID().toString();
        String tokenHash = hashSha256(tokenRaw);

        // Guardar en Base de Datos usando JdbcTemplate
        LocalDateTime exp = LocalDateTime.now().plusMinutes(15);
        Integer tokenRecId = jdbcTemplate.queryForObject(
            "INSERT INTO tokens_recuperacion (usuario_admin_id, token_hash, canal, fecha_expiracion, utilizado, intentos_fallidos, empresa_id) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
            Integer.class,
            gerente.getId(), tokenHash, "CORREO", exp, false, 0, tenantId
        );

        // Enviar notificación simulando un envío seguro
        String link = "http://localhost:5173/recuperar-clave?token=" + tokenRaw + "&identificacion=" + gerente.getUsername();
        
        notificacionService.enviarRecuperacionCorreoAdmin(gerente, link);

        // Registro de Auditoria usando JdbcTemplate
        String valorAnteriorJson = "{\"solicitud\":\"saas_manager\"}";
        String valorNuevoJson = String.format("{\"tokenHash\":\"%s\",\"expira\":\"%s\"}", tokenHash, exp.toString());

        jdbcTemplate.update(
            "INSERT INTO logs_auditoria (usuario_admin_id, accion, tabla_afectada, registro_id, valor_anterior, valor_nuevo, fecha, direccion_ip, dispositivo_info, empresa_id) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP, ?, ?, ?)",
            gerente.getId(),
            "ENVIAR_ENLACE_RESTABLECIMIENTO_ADMIN_SaaS",
            "tokens_recuperacion",
            tokenRecId,
            valorAnteriorJson,
            valorNuevoJson,
            ip != null ? ip : "127.0.0.1",
            userAgent != null ? userAgent : "SaaS Manager",
            tenantId
        );

        return enmascararCorreo(gerente.getCorreo());
    }

    public List<Map<String, Object>> getActiveTenants() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, ruc, razon_social, nombre_comercial FROM empresas WHERE estado = 'ACTIVO'"
        );
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("id", ((Number) row.get("id")).intValue());
            map.put("ruc", (String) row.get("ruc"));
            String nombreComercial = (String) row.get("nombre_comercial");
            String razonSocial = (String) row.get("razon_social");
            map.put("name", nombreComercial != null && !nombreComercial.trim().isEmpty() ? nombreComercial : razonSocial);
            result.add(map);
        }
        return result;
    }
}
