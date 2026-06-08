package com.cooperativa.core.controller;

import com.cooperativa.core.dto.DesembolsoRequestDTO;
import com.cooperativa.core.dto.PagoRequestDTO;
import com.cooperativa.core.model.Credito;
import com.cooperativa.core.service.CreditoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/creditos")
@CrossOrigin(origins = "*")
public class CreditoController {

    @Autowired
    private CreditoService creditoService;

    // ==========================================
    // NUEVOS ENDPOINTS CRUD AÑADIDOS
    // ==========================================

    @PostMapping("/solicitar")
    public ResponseEntity<?> solicitarCredito(@RequestBody Credito credito) {
        try {
            return ResponseEntity.ok(creditoService.crearSolicitud(credito));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listarTodos() {
        return ResponseEntity.ok(creditoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(creditoService.obtenerPorId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/aprobar")
    public ResponseEntity<?> aprobarCredito(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(creditoService.aprobarCredito(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==========================================
    // TU ENDPOINT DE DESEMBOLSO INTACTO
    // ==========================================

    @PostMapping("/desembolsar")
    public ResponseEntity<?> desembolsar(
            @RequestBody DesembolsoRequestDTO requestDTO,
            HttpServletRequest request) {

        if (requestDTO.getCreditoId() == null || requestDTO.getCuentaAhorrosId() == null) {
            return ResponseEntity.badRequest().body("Error: Los campos 'creditoId' y 'cuentaAhorrosId' son obligatorios.");
        }

        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            Credito creditoDesembolsado = creditoService.desembolsarCredito(
                    requestDTO.getCreditoId(),
                    requestDTO.getCuentaAhorrosId(),
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            );
            return ResponseEntity.ok(creditoDesembolsado);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getLocalizedMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno en el core financiero: " + e.getMessage());
        }
    }

    /**
     * Endpoint para registrar el pago de cuotas de un credito amortizado.
     * URL: http://localhost:8080/api/v1/creditos/pagar
     */
    @PostMapping("/pagar")
    public ResponseEntity<?> pagar(
            @Valid @RequestBody PagoRequestDTO requestDTO,
            HttpServletRequest request) {

        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            Credito creditoPagado = creditoService.registrarPago(
                    requestDTO,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            );
            return ResponseEntity.ok(creditoPagado);

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getLocalizedMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno en el core financiero: " + e.getMessage());
        }
    }
}