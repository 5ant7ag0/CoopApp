package com.cooperativa.core.controller;

import com.cooperativa.core.dto.LoginRequestDTO;
import com.cooperativa.core.dto.LoginResponseDTO;
import com.cooperativa.core.dto.SocioRegisterRequestDTO;
import com.cooperativa.core.security.PublicEndpoint;
import com.cooperativa.core.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador para la gestion de seguridad, login de canales digitales
 * y registros de acceso. Todo el controlador es publico.
 */
@RestController
@RequestMapping("/auth")
@PublicEndpoint // Permite el acceso sin token JWT previo a todos los metodos de esta clase
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Inicio de sesion para personal administrativo de la cooperativa (Backoffice).
     */
    @PostMapping("/admin/login")
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
     * Cierre de sesion e invalidacion del token en base de datos.
     */
    @PostMapping("/logout")
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
}
