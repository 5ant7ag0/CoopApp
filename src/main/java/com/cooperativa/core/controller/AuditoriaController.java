package com.cooperativa.core.controller;

import com.cooperativa.core.service.LogsAuditoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auditoria")
@CrossOrigin(origins = "*")
public class AuditoriaController {

    @Autowired
    private LogsAuditoriaService auditoriaService;

    /**
     * Endpoint exclusivo para el rol de AUDITOR.
     * URL: http://localhost:8080/api/v1/auditoria
     */
    @GetMapping
    public ResponseEntity<?> listarAuditoria(jakarta.servlet.http.HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!"AUDITOR_INTERNO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Se requiere rol de Auditor Interno para ver las trazas de auditoría.");
        }

        try {
            return ResponseEntity.ok(auditoriaService.obtenerAuditoriaGlobal());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al consultar las trazas de auditoria: " + e.getMessage());
        }
    }
}