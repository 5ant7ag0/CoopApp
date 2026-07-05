package com.cooperativa.core.controller;

import com.cooperativa.core.service.ContabilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cooperativa.core.security.RequiresRoles;

@RestController
@RequestMapping("/contabilidad")
@CrossOrigin(origins = "*")
@RequiresRoles({"CONTADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
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

    /**
     * Obtiene el catálogo de cuentas (Plan de Cuentas) de la institución activa.
     * URL: http://localhost:8080/api/v1/contabilidad/plan-cuentas
     */
    @GetMapping("/plan-cuentas")
    @RequiresRoles({"CONTADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS", "ADMINISTRADOR"})
    public ResponseEntity<?> obtenerPlanCuentas() {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerPlanCuentas());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtiene los asientos del libro diario en un rango de fechas de forma paginada.
     * URL: http://localhost:8080/api/v1/contabilidad/diario?desde=YYYY-MM-DD&hasta=YYYY-MM-DD&page=0&size=10
     */
    @GetMapping("/diario")
    public ResponseEntity<?> obtenerLibroDiario(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerLibroDiarioPaginado(desde, hasta, page, size));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtiene los movimientos del libro mayor para una cuenta y rango de fechas específicos (de forma paginada u opcionalmente completa).
     * URL: http://localhost:8080/api/v1/contabilidad/mayor?cuentaId=X&desde=YYYY-MM-DD&hasta=YYYY-MM-DD&page=0&size=50
     */
    @GetMapping("/mayor")
    public ResponseEntity<?> obtenerLibroMayor(
            @RequestParam Integer cuentaId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerLibroMayor(cuentaId, desde, hasta, page, size));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtiene los detalles de un asiento contable específico por su número identificador.
     * URL: http://localhost:8080/api/v1/contabilidad/asiento/{numeroAsiento}
     */
    @GetMapping("/asiento/{numeroAsiento}")
    public ResponseEntity<?> obtenerAsientoPorNumero(@PathVariable String numeroAsiento) {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerAsientoPorNumero(numeroAsiento));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Crea una nueva subcuenta contable.
     * URL: POST http://localhost:8080/api/v1/contabilidad/plan-cuentas
     */
    @PostMapping("/plan-cuentas")
    @RequiresRoles({"CONTADOR"})
    public ResponseEntity<?> crearSubcuenta(@RequestBody CrearSubcuentaRequest request) {
        try {
            return ResponseEntity.ok(contabilidadService.crearSubcuenta(request.padreId(), request.nombreCuenta(), request.esMovimiento()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Activa o desactiva una cuenta contable.
     * URL: PUT http://localhost:8080/api/v1/contabilidad/plan-cuentas/{id}/estado?estado=INACTIVO
     */
    @PutMapping("/plan-cuentas/{id}/estado")
    @RequiresRoles({"CONTADOR"})
    public ResponseEntity<?> cambiarEstadoCuenta(
            @PathVariable Integer id,
            @RequestParam String estado) {
        try {
            return ResponseEntity.ok(contabilidadService.cambiarEstadoCuenta(id, estado));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Genera el Estado de Resultados (Pérdidas y Ganancias) para el rango de fechas.
     * URL: http://localhost:8080/api/v1/contabilidad/estado-resultados?desde=YYYY-MM-DD&hasta=YYYY-MM-DD
     */
    @GetMapping("/estado-resultados")
    public ResponseEntity<?> obtenerEstadoResultados(
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerEstadoResultados(desde, hasta));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Genera el Balance General a una fecha de corte específica.
     * URL: http://localhost:8080/api/v1/contabilidad/balance-general?fechaCorte=YYYY-MM-DD
     */
    @GetMapping("/balance-general")
    public ResponseEntity<?> obtenerBalanceGeneral(
            @RequestParam(required = false) String fechaCorte) {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerBalanceGeneral(fechaCorte));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Obtiene el historial de cierres fiscales anuales.
     * URL: GET http://localhost:8080/api/v1/contabilidad/cierres
     */
    @GetMapping("/cierres")
    public ResponseEntity<?> obtenerCierresHistoricos() {
        try {
            return ResponseEntity.ok(contabilidadService.obtenerCierresHistoricos());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Ejecuta el cierre fiscal anual para un año seleccionado.
     * URL: POST http://localhost:8080/api/v1/contabilidad/cierre
     */
    @PostMapping("/cierre")
    @RequiresRoles({"CONTADOR"})
    public ResponseEntity<?> ejecutarCierreAnual(
            @RequestBody CierreRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        try {
            String confirmacionEsperada = "CERRAR " + request.anioFiscal();
            if (request.confirmacion() == null || !request.confirmacion().trim().equals(confirmacionEsperada)) {
                return ResponseEntity.badRequest().body("Error: La frase de confirmación es incorrecta o no coincide.");
            }
            String username = (String) httpRequest.getAttribute("authUsername");
            return ResponseEntity.ok(contabilidadService.ejecutarCierreAnual(request.anioFiscal(), request.cuentaPatrimonialId(), username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // DTO para la creación de subcuentas
    public record CrearSubcuentaRequest(Integer padreId, String nombreCuenta, Boolean esMovimiento) {}

    // DTO para el cierre fiscal anual
    public record CierreRequest(int anioFiscal, Integer cuentaPatrimonialId, String confirmacion) {}
}
