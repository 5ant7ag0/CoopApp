package com.cooperativa.core.controller;

import com.cooperativa.core.service.ContabilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contabilidad")
@CrossOrigin(origins = "*")
public class ContabilidadController {

    @Autowired
    private ContabilidadService contabilidadService;

    /**
     * Endpoint exclusivo para auditoria/contabilidad del inquilino (Tenant) activo.
     * Lista los asientos generados el dia de hoy (Cabecera y detalle compuesto).
     * URL: http://localhost:8080/api/v1/contabilidad/asientos/hoy
     */
    @GetMapping("/asientos/hoy")
    public ResponseEntity<?> obtenerAsientosDeHoy() {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerAsientosDeHoy());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
