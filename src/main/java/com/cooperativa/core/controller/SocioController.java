package com.cooperativa.core.controller;

import com.cooperativa.core.dto.SocioRequestDTO;
import com.cooperativa.core.service.SocioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/socios")
@CrossOrigin(origins = "*")
public class SocioController {

    @Autowired
    private SocioService socioService;

    @PostMapping
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
            return ResponseEntity.badRequest().body(e.getMessage());
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
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody SocioRequestDTO socioDto) {
        try {
            return ResponseEntity.ok(socioService.actualizarSocio(id, socioDto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/avatar")
    public ResponseEntity<?> subirAvatar(@PathVariable Integer id, @RequestParam("file") MultipartFile file) {
        try {
            String avatarUrl = socioService.guardarAvatar(id, file);
            return ResponseEntity.ok(java.util.Map.of("avatarUrl", avatarUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/avatar")
    public ResponseEntity<?> eliminarAvatar(@PathVariable Integer id) {
        try {
            socioService.eliminarAvatar(id);
            return ResponseEntity.ok("Foto de perfil eliminada correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            socioService.eliminarLogico(id);
            return ResponseEntity.ok("Socio inactivado correctamente en el sistema.");
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
