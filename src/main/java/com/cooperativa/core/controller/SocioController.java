package com.cooperativa.core.controller;

import com.cooperativa.core.dto.SocioRequestDTO;
import com.cooperativa.core.service.SocioService;
import com.cooperativa.core.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.cooperativa.core.security.RequiresRoles;

@RestController
@RequestMapping("/socios")
@CrossOrigin(origins = "*")
@RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR"})
public class SocioController {

    @Autowired
    private SocioService socioService;

    @Autowired
    private AuthService authService;

    @PostMapping
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> crear(@Valid @RequestBody SocioRequestDTO socioDto) {
        try {
            return ResponseEntity.ok(socioService.crearSocio(socioDto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listarTodos() {
        try {
            return ResponseEntity.ok(socioService.obtenerTodos());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage() + " | Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "null"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(socioService.obtenerPorId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody SocioRequestDTO socioDto) {
        try {
            return ResponseEntity.ok(socioService.actualizarSocio(id, socioDto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/avatar")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "SOCIO"})
    public ResponseEntity<?> subirAvatar(@PathVariable Integer id, @RequestParam("file") MultipartFile file) {
        try {
            String avatarUrl = socioService.guardarAvatar(id, file);
            return ResponseEntity.ok(java.util.Map.of("avatarUrl", avatarUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/cedula-frontal")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> subirCedulaFrontal(
            @PathVariable Integer id, 
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("authUsername");
            String ip = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String url = socioService.guardarCedulaFrontal(id, file, username, ip, userAgent);
            return ResponseEntity.ok(java.util.Map.of("fotoCedulaFrontalUrl", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/cedula-posterior")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> subirCedulaPosterior(
            @PathVariable Integer id, 
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("authUsername");
            String ip = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String url = socioService.guardarCedulaPosterior(id, file, username, ip, userAgent);
            return ResponseEntity.ok(java.util.Map.of("fotoCedulaPosteriorUrl", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/firma")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> subirFirma(
            @PathVariable Integer id, 
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("authUsername");
            String ip = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String url = socioService.guardarFirma(id, file, username, ip, userAgent);
            return ResponseEntity.ok(java.util.Map.of("firmaUrl", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/avatar")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "SOCIO"})
    public ResponseEntity<?> eliminarAvatar(@PathVariable Integer id) {
        try {
            socioService.eliminarAvatar(id);
            return ResponseEntity.ok("Foto de perfil eliminada correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            socioService.eliminarLogico(id);
            return ResponseEntity.ok("Socio inactivado correctamente en el sistema.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/enviar-restablecimiento")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> enviarRestablecimiento(
            @PathVariable Integer id,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String ip = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String correoEnmascarado = authService.enviarEnlaceRestablecimiento(id, ip, userAgent);
            return ResponseEntity.ok(java.util.Map.of("message", "Enlace enviado al correo " + correoEnmascarado + " del socio."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/buscar")
    public ResponseEntity<?> buscar(@RequestParam String identificacion) {
        try {
            return ResponseEntity.ok(socioService.buscarPorIdentificacion(identificacion));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
