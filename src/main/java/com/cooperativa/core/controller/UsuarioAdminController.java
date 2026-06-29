package com.cooperativa.core.controller;

import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.service.UsuarioAdminService;
import com.cooperativa.core.repository.CajasVentanillaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.cooperativa.core.security.RequiresRoles;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
@RequiresRoles({"GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
public class UsuarioAdminController {

    @Autowired
    private UsuarioAdminService usuarioService;

    @Autowired
    private CajasVentanillaRepository cajasVentanillaRepository;

    private boolean esAltaGerencia(String rol) {
        return "GERENTE_GENERAL".equals(rol) || "SUPER_ADMIN_SAAS".equals(rol);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody UsuariosAdmin usuario, jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!esAltaGerencia(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General para crear usuarios.");
        }

        try {
            return ResponseEntity.ok(usuarioService.crearUsuario(usuario));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listarTodos(jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!esAltaGerencia(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General para listar usuarios.");
        }

        return ResponseEntity.ok(usuarioService.obtenerTodos());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody UsuariosAdmin usuario, jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!esAltaGerencia(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General para actualizar usuarios.");
        }

        try {
            return ResponseEntity.ok(usuarioService.actualizarUsuario(id, usuario));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> inactivar(@PathVariable Integer id, jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!esAltaGerencia(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General para inactivar usuarios.");
        }

        try {
            usuarioService.inactivarUsuario(id);
            return ResponseEntity.ok("Empleado inactivado correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/auditoria")
    public ResponseEntity<?> obtenerAuditoriaEmpleado(
            @PathVariable Integer id, 
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!esAltaGerencia(rol) && !"AUDITOR_INTERNO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General o Auditor Interno para ver auditorías.");
        }

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(usuarioService.obtenerLogsEmpleado(id, fechaInicio, fechaFin, pageable));
    }

    @GetMapping("/cajas-disponibles")
    public ResponseEntity<?> listarCajasDisponibles() {
        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        return ResponseEntity.ok(cajasVentanillaRepository.findByEmpresaIdAndEstado(tenantId, "ACTIVA"));
    }

    @PostMapping("/cambiar-password-inicio")
    @RequiresRoles({"GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO", "CAJERO", "CONTADOR"})
    public ResponseEntity<?> cambiarClaveInicio(@RequestBody Map<String, String> payload, jakarta.servlet.http.HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        if (username == null) {
            return ResponseEntity.status(401).body("Acceso denegado: No autenticado.");
        }
        String passwordNueva = payload.get("passwordNueva");
        try {
            usuarioService.cambiarClaveProximoInicio(username, passwordNueva);
            return ResponseEntity.ok("Contraseña actualizada con éxito.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}