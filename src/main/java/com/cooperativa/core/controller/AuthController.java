package com.cooperativa.core.controller;

import com.cooperativa.core.dto.LoginRequestDTO;
import com.cooperativa.core.dto.LoginResponseDTO;
import com.cooperativa.core.dto.SocioRegisterRequestDTO;
import com.cooperativa.core.dto.UserProfileResponseDTO;
import com.cooperativa.core.dto.CambioClaveRequestDTO;
import com.cooperativa.core.dto.SolicitudRecuperacionDTO;
import com.cooperativa.core.dto.RestablecerClaveRequestDTO;
import com.cooperativa.core.security.PublicEndpoint;
import com.cooperativa.core.security.RequiresRoles;
import com.cooperativa.core.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador para la gestion de seguridad, login de canales digitales
 * y registros de acceso.
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Inicio de sesion para personal administrativo de la cooperativa (Backoffice).
     */
    @PostMapping("/admin/login")
    @PublicEndpoint
    public ResponseEntity<?> loginAdmin(@Valid @RequestBody LoginRequestDTO requestDTO, HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            LoginResponseDTO response = authService.loginAdmin(
                    requestDTO.getUsername(),
                    requestDTO.getPassword(),
                    ip,
                    userAgent != null ? userAgent : "Desconocido"
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error durante la autenticacion administrativa: " + e.getMessage());
        }
    }

    /**
     * Inicio de sesion para socios (Canales Digitales / App Movil).
     */
    @PostMapping("/socio/login")
    @PublicEndpoint
    public ResponseEntity<?> loginSocio(@Valid @RequestBody LoginRequestDTO requestDTO, HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            LoginResponseDTO response = authService.loginSocio(
                    requestDTO.getUsername(), // Recibe la identificacion (cedula/RUC)
                    requestDTO.getPassword(),
                    ip,
                    userAgent != null ? userAgent : "Desconocido"
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error durante la autenticacion del socio: " + e.getMessage());
        }
    }

    /**
     * Registro de contrasena digital para un socio que ya existe en el core banking.
     */
    @PostMapping("/socio/registrar")
    @PublicEndpoint
    public ResponseEntity<?> registrarSocio(@Valid @RequestBody SocioRegisterRequestDTO requestDTO) {
        try {
            authService.registrarCredencialesSocio(requestDTO.getIdentificacion(), requestDTO.getPassword());
            return ResponseEntity.ok("Credenciales digitales registradas exitosamente. Ya puede iniciar sesion.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error en el registro del socio: " + e.getMessage());
        }
    }

    /**
     * Obtiene la informacion del perfil del usuario autenticado actual.
     */
    @GetMapping("/me")
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL", "OFICIAL_DE_CREDITO", "CAJERO", "AUDITOR_INTERNO", "CONTADOR", "SOCIO"})
    public ResponseEntity<?> me(HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("authUsername");
            String rol = (String) request.getAttribute("authRol");
            Integer tenantId = (Integer) request.getAttribute("authTenantId");
            if (username == null || rol == null || tenantId == null) {
                return ResponseEntity.status(401).body("Error: Usuario no autenticado en el contexto.");
            }
            UserProfileResponseDTO perfil = authService.obtenerPerfil(username, rol, tenantId);
            return ResponseEntity.ok(perfil);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Cierre de sesion e invalidacion del token en base de datos.
     */
    @PostMapping("/logout")
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL", "OFICIAL_DE_CREDITO", "CAJERO", "AUDITOR_INTERNO", "CONTADOR", "SOCIO"})
    public ResponseEntity<?> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            authService.logout(token);
        }
        return ResponseEntity.ok("Sesion cerrada correctamente.");
    }

    /**
     * Obtiene la direccion IP del cliente limpiando el formato local de IPv6.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }

    /**
     * Endpoint para que el socio autenticado cambie su contraseña digital.
     * URL: POST http://localhost:8080/api/v1/auth/socio/cambiar-clave
     */
    @PostMapping("/socio/cambiar-clave")
    public ResponseEntity<?> cambiarClaveSocio(
            @Valid @RequestBody CambioClaveRequestDTO requestDTO,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");

        if (username == null || !"SOCIO".equals(rol)) {
            return ResponseEntity.status(403).body("Error: Solo los socios autenticados pueden cambiar su clave digital.");
        }

        try {
            authService.cambiarClaveSocio(username, requestDTO.getPasswordActual(), requestDTO.getPasswordNueva());
            return ResponseEntity.ok("Contraseña digital cambiada exitosamente.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al cambiar la contraseña: " + e.getMessage());
        }
    }

    /**
     * Endpoint público para solicitar la recuperación de la contraseña digital.
     * URL: POST http://localhost:8080/api/v1/auth/recuperar/solicitar
     */
    @PostMapping("/recuperar/solicitar")
    @PublicEndpoint
    public ResponseEntity<?> solicitarRecuperacion(
            @Valid @RequestBody SolicitudRecuperacionDTO requestDTO,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            String correoEnmascarado = authService.solicitarRecuperacion(
                    requestDTO.getIdentificacion(),
                    requestDTO.getCanal(),
                    ip,
                    userAgent != null ? userAgent : "Desconocido"
            );
            return ResponseEntity.ok(java.util.Map.of(
                "message", "Código/enlace de recuperación enviado exitosamente por " + requestDTO.getCanal() + ".",
                "correoEnmascarado", correoEnmascarado
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al solicitar la recuperación: " + e.getMessage());
        }
    }

    /**
     * Endpoint público para validar el token OTP sin consumirlo (para UI modal).
     * URL: POST http://localhost:8080/api/v1/auth/recuperar/validar-token
     */
    @PostMapping("/recuperar/validar-token")
    @PublicEndpoint
    public ResponseEntity<?> validarTokenRecuperacion(
            @RequestBody java.util.Map<String, String> payload,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String identificacion = payload.get("identificacion");
        String token = payload.get("token");
        try {
            authService.validarToken(identificacion, token, ip, userAgent != null ? userAgent : "Desconocido");
            return ResponseEntity.ok(java.util.Map.of("message", "Código OTP válido."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al validar código: " + e.getMessage());
        }
    }

    /**
     * Endpoint público para validar el token de recuperación y cambiar la contraseña.
     * URL: POST http://localhost:8080/api/v1/auth/recuperar/validar-cambiar
     */
    @PostMapping("/recuperar/validar-cambiar")
    @PublicEndpoint
    public ResponseEntity<?> validarYRestablecerClave(
            @Valid @RequestBody RestablecerClaveRequestDTO requestDTO,
            HttpServletRequest request) {
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        try {
            authService.validarYRestablecerClave(
                    requestDTO.getIdentificacion(),
                    requestDTO.getToken(),
                    requestDTO.getPasswordNueva(),
                    ip,
                    userAgent != null ? userAgent : "Desconocido"
            );
            return ResponseEntity.ok(java.util.Map.of("message", "Contraseña restablecida y cuenta activada con éxito."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al restablecer la contraseña: " + e.getMessage());
        }
    }

    /**
     * Endpoint público para listar las cooperativas activas.
     * URL: GET http://localhost:8080/api/v1/auth/tenants
     */
    @GetMapping("/tenants")
    @PublicEndpoint
    public ResponseEntity<?> getPublicTenants() {
        try {
            return ResponseEntity.ok(authService.getActiveTenants());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al listar cooperativas: " + e.getMessage());
        }
    }
}
