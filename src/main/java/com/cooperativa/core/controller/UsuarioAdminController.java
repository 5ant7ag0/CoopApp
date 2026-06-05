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
    public ResponseEntity<?> crear(@RequestBody UsuariosAdmin usuario) {
        try {
            return ResponseEntity.ok(usuarioService.crearUsuario(usuario));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listarTodos() {
        return ResponseEntity.ok(usuarioService.obtenerTodos());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> inactivar(@PathVariable Integer id) {
        try {
            usuarioService.inactivarUsuario(id);
            return ResponseEntity.ok("Empleado inactivado correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}