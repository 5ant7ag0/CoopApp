package com.cooperativa.core.controller;

import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.service.UsuarioAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioAdminController {

    @Autowired
    private UsuarioAdminService usuarioService;

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody UsuariosAdmin usuario, jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!"GERENTE_GENERAL".equals(rol)) {
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
        if (!"GERENTE_GENERAL".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General para listar usuarios.");
        }

        return ResponseEntity.ok(usuarioService.obtenerTodos());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> inactivar(@PathVariable Integer id, jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!"GERENTE_GENERAL".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Gerente General para inactivar usuarios.");
        }

        try {
            usuarioService.inactivarUsuario(id);
            return ResponseEntity.ok("Empleado inactivado correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}