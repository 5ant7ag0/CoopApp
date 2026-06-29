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
import java.util.Map;
import java.util.Optional;
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
    public void solicitarRecuperacion(String identificacion, String canal, String ip, String userAgent) {
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
        if ("CORREO".equals(canal)) {
            tokenRaw = java.util.UUID.randomUUID().toString();
        } else if ("SMS".equals(canal)) {
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
    }

    /**
     * Valida el token o código OTP de recuperación e inyecta la nueva clave digital.
     * Mitiga fuerza bruta invalidando el token tras 3 intentos fallidos consecutivos de validación.
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = IllegalArgumentException.class)
    public void validarYRestablecerClave(String identificacion, String token, String passwordNueva, String ip, String userAgent) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede validar token sin especificar la institucion (X-Tenant-ID).");
        }

        if (identificacion == null || identificacion.trim().isEmpty()) {
            throw new IllegalArgumentException("La identificacion del socio es requerida.");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("El token o codigo OTP es requerido.");
        }
        if (passwordNueva == null || passwordNueva.trim().isEmpty()) {
            throw new IllegalArgumentException("La nueva contrasena es requerida.");
        }

        // 1. Buscar socio
        Socio socio = socioRepository.findByIdentificacionAndEmpresaId(identificacion, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Socio no encontrado con la identificacion provista."));

        // 2. Buscar ultimo token de recuperacion no utilizado y no expirado para este socio
        LocalDateTime ahora = LocalDateTime.now();
        TokensRecuperacion tokenRec = tokensRecuperacionRepository
                .findFirstBySocioIdAndUtilizadoFalseAndFechaExpiracionAfterOrderByCreatedAtDesc(socio.getId(), ahora)
                .orElseThrow(() -> new IllegalArgumentException("Token o codigo OTP invalido o expirado."));

        // 3. Validar intentos fallidos previos
        if (tokenRec.getIntentosFallidos() >= 3) {
            tokenRec.setUtilizado(true);
            tokensRecuperacionRepository.save(tokenRec);
            throw new IllegalArgumentException("El token o codigo OTP ha sido invalidado por exceso de intentos fallidos.");
        }

        // 4. Comparar hashes
        String hashIngresado = hashSha256(token);
        if (!tokenRec.getTokenHash().equals(hashIngresado)) {
            // Incrementar intentos fallidos
            tokenRec.setIntentosFallidos(tokenRec.getIntentosFallidos() + 1);
            if (tokenRec.getIntentosFallidos() >= 3) {
                tokenRec.setUtilizado(true);
            }
            tokensRecuperacionRepository.save(tokenRec);

            // Log de auditoria para intento fallido
            LogsAuditoria auditLog = new LogsAuditoria();
            auditLog.setSocio(socio);
            auditLog.setAccion("VALIDAR_RECUPERACION_FALLIDO");
            auditLog.setTablaAfectada("tokens_recuperacion");
            auditLog.setRegistroId(tokenRec.getId());
            auditLog.setDireccionIp(ip != null ? ip : "127.0.0.1");
            auditLog.setDispositivoInfo(userAgent != null ? userAgent : "Desconocido");
            auditLog.setValorAnterior(Map.of("intentosFallidos", tokenRec.getIntentosFallidos() - 1));
            auditLog.setValorNuevo(Map.of("intentosFallidos", tokenRec.getIntentosFallidos()));
            logsAuditoriaService.registrarLog(auditLog);

            if (tokenRec.getIntentosFallidos() >= 3) {
                throw new IllegalArgumentException("El token o codigo OTP ha sido invalidado por exceso de intentos fallidos.");
            } else {
                throw new IllegalArgumentException("Token o codigo OTP invalido.");
            }
        }

        // 5. Si es correcto, actualizar credenciales del socio (crear si no existen)
        SociosCredenciales creds = credencialesRepository.findBySocioId(socio.getId())
                .orElseGet(() -> {
                    SociosCredenciales newCreds = new SociosCredenciales();
                    newCreds.setSocio(socio);
                    return newCreds;
                });

        creds.setPasswordHash(encryptionService.hashPassword(passwordNueva));
        creds.setEstadoAcceso("ACTIVO");
        creds.setIntentosFallidos(0);
        creds.setBloqueadoHasta(null);
        credencialesRepository.save(creds);

        // 6. Marcar token como utilizado
        tokenRec.setUtilizado(true);
        tokensRecuperacionRepository.save(tokenRec);

        // 7. Registro de Auditoria
        LogsAuditoria auditLog = new LogsAuditoria();
        auditLog.setSocio(socio);
        auditLog.setAccion("RESTABLECER_CLAVE_EXITOSO");
        auditLog.setTablaAfectada("socios_credenciales");
        auditLog.setRegistroId(creds.getId());
        auditLog.setDireccionIp(ip != null ? ip : "127.0.0.1");
        auditLog.setDispositivoInfo(userAgent != null ? userAgent : "Desconocido");
        auditLog.setValorAnterior(Map.of("estadoAcceso", "BLOQUEADA"));
        auditLog.setValorNuevo(Map.of("estadoAcceso", "ACTIVO"));
        logsAuditoriaService.registrarLog(auditLog);
    }
}
