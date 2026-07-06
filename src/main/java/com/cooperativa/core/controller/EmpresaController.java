package com.cooperativa.core.controller;

import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.service.EmpresaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.cooperativa.core.security.RequiresRoles;

@RestController
@RequestMapping("/empresas")
@CrossOrigin(origins = "*")
public class EmpresaController {

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private com.cooperativa.core.repository.LogsAuditoriaRepository logsAuditoriaRepository;

    /**
     * Obtiene el perfil de la cooperativa en base al X-Tenant-ID enviado.
     * URL: http://localhost:8080/api/v1/empresas/mi-perfil
     */
    @GetMapping("/mi-perfil")
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL", "OFICIAL_DE_CREDITO", "CAJERO", "AUDITOR_INTERNO", "CONTADOR", "SOCIO"})
    public ResponseEntity<?> obtenerPerfil() {
        try {
            return ResponseEntity.ok(empresaService.obtenerMiEmpresa());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Actualiza los datos institucionales y financieros de la cooperativa activa.
     */
    @PutMapping("/mi-perfil")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL"})
    public ResponseEntity<?> actualizarPerfil(
            @RequestBody Empresa empresa,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("authUsername");
            String ipUsuario = request.getRemoteAddr();
            String dispositivo = request.getHeader("User-Agent");

            if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
                ipUsuario = "127.0.0.1";
            }

            return ResponseEntity.ok(empresaService.actualizarMiEmpresa(
                    empresa,
                    username,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtiene el historial de auditoría de modificaciones a los parámetros de la empresa.
     */
    @GetMapping("/mi-perfil/logs-auditoria")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL"})
    public ResponseEntity<?> obtenerLogsAuditoria() {
        try {
            Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
            return ResponseEntity.ok(logsAuditoriaRepository.findByEmpresaIdAndTablaAfectadaAndAccionOrderByFechaDesc(
                    tenantId, "empresas", "ACTUALIZAR_PARAMETROS"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Sube el logo institucional de la empresa.
     */
    @PostMapping("/mi-perfil/logo")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL"})
    public ResponseEntity<?> subirLogo(
            @RequestParam("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            String username = (String) request.getAttribute("authUsername");
            String ipUsuario = request.getRemoteAddr();
            String dispositivo = request.getHeader("User-Agent");

            if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
                ipUsuario = "127.0.0.1";
            }

            String urlLogo = empresaService.guardarLogo(
                    file,
                    username,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            );
            return ResponseEntity.ok(java.util.Map.of("logoUrl", urlLogo));
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
    @RequiresRoles({"SUPER_ADMIN_SAAS"})
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
    @RequiresRoles({"SUPER_ADMIN_SAAS"})
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
    @RequiresRoles({"SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            empresaService.eliminarEmpresa(id);
            return ResponseEntity.ok("Empresa inactivada correctamente en el sistema.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
