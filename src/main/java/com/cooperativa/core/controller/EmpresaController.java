package com.cooperativa.core.controller;

import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.service.EmpresaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/empresas")
@CrossOrigin(origins = "*")
public class EmpresaController {

    @Autowired
    private EmpresaService empresaService;

    /**
     * Obtiene el perfil de la cooperativa en base al X-Tenant-ID enviado.
     * URL: http://localhost:8080/api/v1/empresas/mi-perfil
     */
    @GetMapping("/mi-perfil")
    public ResponseEntity<?> obtenerPerfil() {
        try {
            return ResponseEntity.ok(empresaService.obtenerMiEmpresa());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Actualiza los datos institucionales de la cooperativa activa.
     */
    @PutMapping("/mi-perfil")
    public ResponseEntity<?> actualizarPerfil(@RequestBody Empresa empresa) {
        try {
            return ResponseEntity.ok(empresaService.actualizarMiEmpresa(empresa));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- ENDPOINTS PARA CRUD COMPLETO ---

    /**
     * Crear una nueva empresa (cooperativa).
     */
    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Empresa empresa) {
        try {
            return ResponseEntity.ok(empresaService.crearEmpresa(empresa));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtener todas las empresas.
     */
    @GetMapping
    public ResponseEntity<?> listarTodas() {
        return ResponseEntity.ok(empresaService.obtenerTodas());
    }

    /**
     * Obtener una empresa por su ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(empresaService.obtenerPorId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Actualizar una empresa por su ID.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody Empresa empresa) {
        try {
            return ResponseEntity.ok(empresaService.actualizarEmpresa(id, empresa));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Inactivar (eliminar lógicamente) una empresa por su ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            empresaService.eliminarEmpresa(id);
            return ResponseEntity.ok("Empresa inactivada correctamente en el sistema.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
