package com.cooperativa.core.controller;

import com.cooperativa.core.dto.DesembolsoRequestDTO;
import com.cooperativa.core.dto.PagoRequestDTO;
import com.cooperativa.core.model.Credito;
import com.cooperativa.core.model.CuotasAmortizacion;
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
    public ResponseEntity<?> solicitarCredito(
            @RequestBody Credito credito,
            @RequestParam(value = "presencial", required = false, defaultValue = "false") boolean presencial) {
        try {
            return ResponseEntity.ok(creditoService.crearSolicitud(credito, presencial));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listarTodos() {
        return ResponseEntity.ok(creditoService.obtenerTodos());
    }

    @GetMapping("/mis-creditos")
    public ResponseEntity<?> obtenerMisCreditos(HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        if (username == null || !"SOCIO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado. Solo los socios pueden ver sus créditos.");
        }
        try {
            return ResponseEntity.ok(creditoService.obtenerCreditosSocio(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
    public ResponseEntity<?> aprobarCredito(@PathVariable Integer id, HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!"GERENTE_GENERAL".equals(rol) && !"OFICIAL_DE_CREDITO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Permisos insuficientes para aprobar créditos.");
        }

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

        String rol = (String) request.getAttribute("authRol");
        if (!"GERENTE_GENERAL".equals(rol) && !"OFICIAL_DE_CREDITO".equals(rol) && !"CAJERO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Permisos insuficientes para desembolsar créditos.");
        }

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

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            Credito creditoPagado = creditoService.registrarPago(
                    requestDTO,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            );
            return ResponseEntity.ok(creditoPagado);

        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getLocalizedMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno en el core financiero: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/amortizacion")
    public ResponseEntity<?> obtenerAmortizacion(@PathVariable Integer id, HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        try {
            return ResponseEntity.ok(creditoService.obtenerAmortizacion(id, username, rol));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/revisar")
    public ResponseEntity<?> revisarCredito(@PathVariable Integer id, HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        if (!"GERENTE_GENERAL".equals(rol) && !"OFICIAL_DE_CREDITO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Permisos insuficientes.");
        }

        try {
            return ResponseEntity.ok(creditoService.revisarCredito(id, username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/rechazar")
    public ResponseEntity<?> rechazarCredito(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, String> payload,
            HttpServletRequest request) {
        String rol = (String) request.getAttribute("authRol");
        if (!"GERENTE_GENERAL".equals(rol) && !"OFICIAL_DE_CREDITO".equals(rol)) {
            return ResponseEntity.status(403).body("Acceso denegado: Permisos insuficientes para rechazar créditos.");
        }

        String motivo = payload.get("motivo");
        if (motivo == null || motivo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: El motivo de rechazo es obligatorio.");
        }

        try {
            return ResponseEntity.ok(creditoService.rechazarCredito(id, motivo.trim()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}