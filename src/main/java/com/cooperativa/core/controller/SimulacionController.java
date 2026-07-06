package com.cooperativa.core.controller;

import com.cooperativa.core.dto.CuotaSimuladaDTO;
import com.cooperativa.core.service.CreditoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/creditos")

@com.cooperativa.core.security.PublicEndpoint
public class SimulacionController {

    @Autowired
    private CreditoService creditoService;

    /**
     * Endpoint público para simular tablas de amortización sobre la marcha.
     * Consumido por la App Móvil y el Backoffice.
     * URL: http://localhost:8080/api/v1/creditos/simular
     */
    @GetMapping("/simular")
    public ResponseEntity<?> simular(
            @RequestParam BigDecimal monto,
            @RequestParam int plazoMeses,
            @RequestParam BigDecimal tasaAnual,
            @RequestParam String sistema) {

        // Validaciones financieras básicas de entrada de datos
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Error: El monto solicitado debe ser mayor a 0.");
        }
        if (plazoMeses <= 0) {
            return ResponseEntity.badRequest().body("Error: El plazo en meses debe ser mayor a 0.");
        }
        if (tasaAnual == null || tasaAnual.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body("Error: La tasa de interes anual debe ser mayor a 0.");
        }
        if (sistema == null || sistema.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Debe especificar el sistema de amortizacion (FRANCES, ALEMAN o AMERICANO).");
        }

        try {
            // Invoca al motor matemático del core financiero
            List<CuotaSimuladaDTO> tablaProyectada = creditoService.simularTablaAmortizacion(monto, plazoMeses, tasaAnual, sistema);
            return ResponseEntity.ok(tablaProyectada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}