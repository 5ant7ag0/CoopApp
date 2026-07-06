package com.cooperativa.core.controller;

import com.cooperativa.core.dto.AperturaCajaDTO;
import com.cooperativa.core.dto.CierreCajaDTO;
import com.cooperativa.core.model.CajaDiaria;
import com.cooperativa.core.service.CajaDiariaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cooperativa.core.security.RequiresRoles;

@RestController
@RequestMapping("/cajas")

@RequiresRoles({"CAJERO"})
public class CajaDiariaController {

    @Autowired
    private CajaDiariaService cajaDiariaService;

    /**
     * Endpoint para la apertura de la caja diaria.
     * URL: POST http://localhost:8080/api/v1/cajas/aperturar
     */
    @PostMapping("/aperturar")
    public ResponseEntity<?> aperturarCaja(
            @Valid @RequestBody AperturaCajaDTO dto,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            CajaDiaria caja = cajaDiariaService.aperturarCaja(
                    dto,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Ventanilla"
            );
            return ResponseEntity.ok(caja);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al aperturar la caja: " + e.getMessage());
        }
    }

    /**
     * Endpoint para consultar la caja activa (APERTURADA) del cajero en sesión.
     * URL: GET http://localhost:8080/api/v1/cajas/activa
     */
    @GetMapping("/activa")
    public ResponseEntity<?> obtenerCajaActiva(HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        try {
            return cajaDiariaService.obtenerCajaActiva(username)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Endpoint para realizar el arqueo, conciliación y cierre de caja.
     * URL: POST http://localhost:8080/api/v1/cajas/cerrar
     */
    @PostMapping("/cerrar")
    public ResponseEntity<?> cerrarCaja(
            @Valid @RequestBody CierreCajaDTO dto,
            HttpServletRequest request) {

        String username = (String) request.getAttribute("authUsername");
        String rol = (String) request.getAttribute("authRol");
        String ipUsuario = request.getRemoteAddr();
        String dispositivo = request.getHeader("User-Agent");

        if ("0:0:0:0:0:0:0:1".equals(ipUsuario)) {
            ipUsuario = "127.0.0.1";
        }

        try {
            CajaDiaria caja = cajaDiariaService.cerrarCaja(
                    dto,
                    username,
                    rol,
                    ipUsuario,
                    dispositivo != null ? dispositivo : "Ventanilla"
            );
            return ResponseEntity.ok(caja);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al cerrar la caja: " + e.getMessage());
        }
    }

    /**
     * Endpoint para obtener el listado de movimientos de la caja activa del cajero en sesión.
     * URL: GET http://localhost:8080/api/v1/cajas/movimientos
     */
    @GetMapping("/movimientos")
    public ResponseEntity<?> obtenerMovimientosDiarios(HttpServletRequest request) {
        String username = (String) request.getAttribute("authUsername");
        try {
            return ResponseEntity.ok(cajaDiariaService.obtenerMovimientosDiariosCajero(username));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
