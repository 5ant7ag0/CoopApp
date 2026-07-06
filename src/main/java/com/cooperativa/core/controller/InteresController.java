package com.cooperativa.core.controller;

import com.cooperativa.core.dto.DevengoManualRequestDTO;
import com.cooperativa.core.dto.CapitalizacionManualRequestDTO;
import com.cooperativa.core.model.DevengoRegistro;
import com.cooperativa.core.model.CapitalizacionRegistro;
import com.cooperativa.core.security.RequiresRoles;
import com.cooperativa.core.service.InteresAhorroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/intereses")
@RequiresRoles({"GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})

public class InteresController {

    @Autowired
    private InteresAhorroService interesAhorroService;

    /**
     * Endpoint para ejecutar manualmente el devengo diario de intereses.
     * URL: POST http://localhost:8080/api/v1/intereses/devengo-manual
     */
    @PostMapping("/devengo-manual")
    public ResponseEntity<?> ejecutarDevengoManual(
            @Valid @RequestBody DevengoManualRequestDTO dto,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");

        try {
            DevengoRegistro devengo = interesAhorroService.devengarInteresesDiarios(dto.getFecha(), username);
            return ResponseEntity.ok(devengo);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al procesar el devengo manual: " + e.getMessage());
        }
    }

    /**
     * Endpoint para ejecutar manualmente la capitalización mensual de intereses.
     * URL: POST http://localhost:8080/api/v1/intereses/capitalizacion-manual
     */
    @PostMapping("/capitalizacion-manual")
    public ResponseEntity<?> ejecutarCapitalizacionManual(
            @Valid @RequestBody CapitalizacionManualRequestDTO dto,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");

        try {
            CapitalizacionRegistro cap = interesAhorroService.capitalizarInteresesMensuales(dto.getAnio(), dto.getMes(), username);
            return ResponseEntity.ok(cap);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al procesar la capitalización manual: " + e.getMessage());
        }
    }
}
