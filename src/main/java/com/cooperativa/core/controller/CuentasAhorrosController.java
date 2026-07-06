package com.cooperativa.core.controller;

import com.cooperativa.core.dto.CuentasAhorrosRequestDTO;
import com.cooperativa.core.model.CuentasAhorros;
import com.cooperativa.core.service.CuentasAhorrosService;
import com.cooperativa.core.dto.TransferenciaRequestDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.cooperativa.core.security.RequiresRoles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cuentas")
public class CuentasAhorrosController {

    @Autowired
    private CuentasAhorrosService cuentasAhorrosService;

    @PostMapping
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> crear(@Valid @RequestBody CuentasAhorrosRequestDTO cuentaDto) {
        try {
            return ResponseEntity.ok(cuentasAhorrosService.crearCuenta(cuentaDto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR"})
    public ResponseEntity<?> listarTodas() {
        return ResponseEntity.ok(cuentasAhorrosService.obtenerTodas());
    }

    @GetMapping("/mis-cuentas")
    @RequiresRoles({"SOCIO"})
    public ResponseEntity<?> obtenerMisCuentas(HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        try {
            return ResponseEntity.ok(cuentasAhorrosService.obtenerCuentasSocio(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "SOCIO"})
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id, HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        try {
            CuentasAhorros cuenta = cuentasAhorrosService.obtenerPorId(id);
            if ("SOCIO".equals(rol) && !cuenta.getSocio().getIdentificacion().equals(username)) {
                return ResponseEntity.status(403).body("Error de Seguridad: Acceso denegado. No es propietario de la cuenta solicitada.");
            }
            return ResponseEntity.ok(cuenta);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @Valid @RequestBody CuentasAhorrosRequestDTO cuentaDto) {
        try {
            return ResponseEntity.ok(cuentasAhorrosService.actualizarCuenta(id, cuentaDto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            cuentasAhorrosService.eliminarLogico(id);
            return ResponseEntity.ok("Cuenta de ahorros inactivada correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/transacciones")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "SOCIO", "CAJERO"})
    public ResponseEntity<?> obtenerTransacciones(@PathVariable Integer id, HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        try {
            return ResponseEntity.ok(cuentasAhorrosService.obtenerTransacciones(id, username, rol));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Endpoint para buscar un destinatario de transferencia de forma individual y segura por su número de cuenta.
     * URL: GET http://localhost:8080/api/v1/cuentas/buscar-destinatario
     */
    @GetMapping("/buscar-destinatario")
    @RequiresRoles({"SOCIO", "CAJERO"})
    public ResponseEntity<?> buscarDestinatario(
            @RequestParam String numeroCuenta,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");

        try {
            CuentasAhorros cuenta = cuentasAhorrosService.obtenerDestinatarioPorNumero(numeroCuenta);

            // Regla de seguridad: Impedir transferencia a uno mismo
            if (cuenta.getSocio().getIdentificacion().equals(username)) {
                return ResponseEntity.badRequest().body("No se permiten transferencias a su misma cuenta.");
            }

            return ResponseEntity.ok(java.util.Map.of(
                    "id", cuenta.getId(),
                    "numeroCuenta", cuenta.getNumeroCuenta(),
                    "nombresCompletos", cuenta.getSocio().getNombresCompletos(),
                    "tipo", cuenta.getTipo()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al buscar destinatario: " + e.getMessage());
        }
    }

    /**
     * Endpoint para transferencias internas entre socios de la misma cooperativa.
     * URL: POST http://localhost:8080/api/v1/cuentas/transferir
     */
    @PostMapping("/transferir")
    @RequiresRoles({"SOCIO", "CAJERO"})
    public ResponseEntity<?> transferir(
            @Valid @RequestBody TransferenciaRequestDTO requestDTO,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            cuentasAhorrosService.transferirInterna(
                    requestDTO,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            );
            return ResponseEntity.ok("Transferencia interna realizada exitosamente.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al procesar la transferencia: " + e.getMessage());
        }
    }

    /**
     * Endpoint para depósito de efectivo en ventanilla por un cajero.
     * URL: POST http://localhost:8080/api/v1/cuentas/deposito
     */
    @PostMapping("/deposito")
    @RequiresRoles({"CAJERO"})
    public ResponseEntity<?> registrarDeposito(
            @Valid @RequestBody com.cooperativa.core.dto.TransaccionVentanillaDTO requestDTO,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            cuentasAhorrosService.registrarDepositoVentanilla(
                    requestDTO,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Ventanilla"
            );
            return ResponseEntity.ok("Depósito de $" + requestDTO.getMonto() + " registrado correctamente en ventanilla.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al procesar el depósito: " + e.getMessage());
        }
    }

    /**
     * Endpoint para retiro de efectivo en ventanilla por un cajero.
     * URL: POST http://localhost:8080/api/v1/cuentas/retiro
     */
    @PostMapping("/retiro")
    @RequiresRoles({"CAJERO"})
    public ResponseEntity<?> registrarRetiro(
            @Valid @RequestBody com.cooperativa.core.dto.TransaccionVentanillaDTO requestDTO,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            cuentasAhorrosService.registrarRetiroVentanilla(
                    requestDTO,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Ventanilla"
            );
            return ResponseEntity.ok("Retiro de $" + requestDTO.getMonto() + " registrado correctamente en ventanilla.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al procesar el retiro: " + e.getMessage());
        }
    }

    /**
     * Endpoint para generar y obtener el estado de cuenta en formato PDF.
     * URL: GET http://localhost:8080/api/v1/cuentas/{id}/reporte-pdf
     */
    @GetMapping("/{id}/reporte-pdf")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "SOCIO"})
    public ResponseEntity<byte[]> obtenerReportePdf(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer anio,
            @RequestParam(required = false) Integer mes,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");

        try {
            byte[] pdfBytes = cuentasAhorrosService.generarEstadoCuentaPdf(id, username, rol, anio, mes);

            // Obtener el número de cuenta para personalizar el filename
            String numeroCuenta = cuentasAhorrosService.obtenerPorId(id).getNumeroCuenta();

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "inline; filename=\"estado_cuenta_" + numeroCuenta + ".pdf\"")
                    .header("Cache-Control", "no-cache, no-store, must-revalidate")
                    .header("Pragma", "no-cache")
                    .header("Expires", "0")
                    .body(pdfBytes);
        } catch (SecurityException e) {
            return ResponseEntity.status(403)
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .body(e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .body(e.getMessage().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .header("Content-Type", "text/plain;charset=UTF-8")
                    .body(("Error interno al generar el reporte: " + e.getMessage()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Endpoint para buscar la cuenta y ficha del socio para operaciones de caja (por número de cuenta o cédula).
     * URL: GET http://localhost:8080/api/v1/cuentas/buscar-caja
     */
    @GetMapping("/buscar-caja")
    @RequiresRoles({"CAJERO"})
    public ResponseEntity<?> buscarParaCaja(
            @RequestParam String query,
            HttpServletRequest request) {

        Integer tenantId = com.cooperativa.core.config.TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body("Error: No se especificó el X-Tenant-ID.");
        }

        try {
            return ResponseEntity.ok(cuentasAhorrosService.buscarCuentaParaCaja(query, tenantId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al buscar la cuenta: " + e.getMessage());
        }
    }

    /**
     * Endpoint para obtener todas las cuentas (Vista y Aportaciones) de un socio.
     * URL: GET http://localhost:8080/api/v1/cuentas/socio/{socioId}
     */
    @GetMapping("/socio/{socioId}")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "CONTADOR", "CAJERO"})
    public ResponseEntity<?> obtenerCuentasSocioPorId(@PathVariable Integer socioId, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(cuentasAhorrosService.obtenerCuentasSocioPorId(socioId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/aperturar")
    @RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> aperturarCuenta(
            @Valid @RequestBody com.cooperativa.core.dto.AperturaCuentaRequestDTO requestDTO,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            CuentasAhorros nuevaCuenta = cuentasAhorrosService.aperturarCuentaSocio(
                    requestDTO.getSocioId(),
                    requestDTO.getProductoAhorroId(),
                    requestDTO.getMontoInicial(),
                    null, // plazo
                    null, // renovacionAutomatica
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Desconocido"
            );
            return ResponseEntity.ok(nuevaCuenta);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al aperturar la cuenta: " + e.getMessage());
        }
    }

    /**
     * Endpoint para anular/reversar una transacción del Ledger por un cajero bajo aprobación de supervisor.
     * URL: POST http://localhost:8080/api/v1/cuentas/transacciones/{id}/anular
     */
    @PostMapping("/transacciones/{id}/anular")
    @RequiresRoles({"CAJERO"})
    public ResponseEntity<?> anularTransaccion(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        String claveSupervisor = body.get("claveSupervisor");
        if (claveSupervisor == null || claveSupervisor.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: La clave de supervisor es obligatoria.");
        }

        try {
            cuentasAhorrosService.anularTransaccion(
                    id,
                    claveSupervisor,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Ventanilla"
            );
            return ResponseEntity.ok("Transacción anulada y reversada exitosamente.");
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al anular la transacción: " + e.getMessage());
        }
    }

    /**
     * Endpoint para autogestión de apertura de cuenta por parte del socio (banca digital).
     * URL: POST http://localhost:8080/api/v1/cuentas/aperturar-socio
     */
    @PostMapping("/aperturar-socio")
    @RequiresRoles({"SOCIO"})
    public ResponseEntity<?> aperturarSocioSelf(
            @RequestBody java.util.Map<String, Object> body,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }


        try {
            if (!body.containsKey("productoAhorroId") || body.get("productoAhorroId") == null) {
                return ResponseEntity.badRequest().body("Error: El producto de ahorro seleccionado es obligatorio.");
            }
            if (!body.containsKey("montoInicial") || body.get("montoInicial") == null) {
                return ResponseEntity.badRequest().body("Error: El monto de apertura inicial es obligatorio.");
            }

            Integer productoAhorroId = Integer.valueOf(body.get("productoAhorroId").toString());
            java.math.BigDecimal montoInicial = new java.math.BigDecimal(body.get("montoInicial").toString());
            Integer plazoDias = body.containsKey("plazo") && body.get("plazo") != null
                    ? Integer.valueOf(body.get("plazo").toString())
                    : null;
            Boolean renovacionAutomatica = body.containsKey("renovacionAutomatica") && body.get("renovacionAutomatica") != null
                    ? Boolean.valueOf(body.get("renovacionAutomatica").toString())
                    : false;

            com.cooperativa.core.model.Socio socio = cuentasAhorrosService.obtenerSocioPorIdentificacion(username);
            if (socio == null) {
                return ResponseEntity.badRequest().body("Error: No se pudo identificar al socio activo en el sistema.");
            }

            CuentasAhorros nuevaCuenta = cuentasAhorrosService.aperturarCuentaSocio(
                    socio.getId(),
                    productoAhorroId,
                    montoInicial,
                    plazoDias,
                    renovacionAutomatica,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Banca Digital Socio"
            );
            return ResponseEntity.ok(nuevaCuenta);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al procesar la apertura de la cuenta: " + e.getMessage());
        }
    }
}