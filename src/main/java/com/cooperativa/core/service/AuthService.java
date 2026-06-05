package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.LoginResponseDTO;
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
import java.util.Optional;

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

        // Validar contrasena
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

        return new LoginResponseDTO(token, admin.getUsername(), admin.getNombresCompletos(), admin.getRol(), tenantId);
    }

    /**
     * Autenticacion de socios para canales digitales (App Movil / Web Socio).
     */
    @Transactional(rollbackFor = Exception.class)
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
                creds.setEstadoAcceso("SUSPENDIDO");
                creds.setBloqueadoHasta(LocalDateTime.now().plusMinutes(30));
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
}
