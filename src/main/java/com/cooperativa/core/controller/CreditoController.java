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

import com.cooperativa.core.security.RequiresRoles;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/creditos")
public class CreditoController {

    @Autowired
    private CreditoService creditoService;

    // ==========================================
    // NUEVOS ENDPOINTS CRUD AÑADIDOS
    // ==========================================

    @PostMapping("/solicitar")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "SOCIO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> solicitarCredito(
            @RequestBody Credito credito,
            @RequestParam(value = "presencial", required = false, defaultValue = "false") boolean presencial,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        try {
            return ResponseEntity.ok(creditoService.crearSolicitud(credito, presencial, username, rol));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR"})
    public ResponseEntity<?> listarTodos() {
        return ResponseEntity.ok(creditoService.obtenerTodos());
    }

    @GetMapping("/mis-creditos")
    @RequiresRoles({"SOCIO"})
    public ResponseEntity<?> obtenerMisCreditos(HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        try {
            return ResponseEntity.ok(creditoService.obtenerCreditosSocio(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/socio/{socioId}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "CAJERO"})
    public ResponseEntity<?> obtenerCreditosSocioPorId(@PathVariable Integer socioId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(creditoService.obtenerCreditosSocioPorId(socioId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "SOCIO", "CAJERO"})
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(creditoService.obtenerPorId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/aprobar")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> aprobarCredito(@PathVariable Integer id, HttpServletRequest request) {

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
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
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
    @RequiresRoles({"CAJERO", "SOCIO"})
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

    @PostMapping("/{id}/pagar")
    @RequiresRoles({"CAJERO", "SOCIO"})
    public ResponseEntity<?> pagarConIdEnRuta(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, Object> payload,
            HttpServletRequest request) {
        
        PagoRequestDTO requestDTO = new PagoRequestDTO();
        requestDTO.setCreditoId(id);
        requestDTO.setOrigenFondos("CUENTA");
        if (payload.get("cuentaAhorrosId") != null) {
            requestDTO.setCuentaAhorrosId(((Number) payload.get("cuentaAhorrosId")).intValue());
        }
        if (payload.get("monto") != null) {
            requestDTO.setMonto(new java.math.BigDecimal(payload.get("monto").toString()));
        }
        
        return pagar(requestDTO, request);
    }

    @GetMapping("/{id}/amortizacion")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "SOCIO", "CAJERO"})
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
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> revisarCredito(@PathVariable Integer id, HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");

        try {
            return ResponseEntity.ok(creditoService.revisarCredito(id, username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/rechazar")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> rechazarCredito(
            @PathVariable Integer id,
            @RequestBody java.util.Map<String, String> payload,
            HttpServletRequest request) {

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

    @PostMapping("/{id}/pagare")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CAJERO"})
    public ResponseEntity<?> subirPagare(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        try {
            String url = creditoService.guardarPagare(id, file);
            return ResponseEntity.ok(java.util.Map.of("pagareUrl", url));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}