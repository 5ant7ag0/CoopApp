package com.cooperativa.core.controller;

import com.cooperativa.core.model.CuentasAhorros;
import com.cooperativa.core.service.CuentasAhorrosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cuentas")
@CrossOrigin(origins = "*")
public class CuentasAhorrosController {

    @Autowired
    private CuentasAhorrosService cuentasAhorrosService;

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody CuentasAhorros cuenta) {
        try {
            return ResponseEntity.ok(cuentasAhorrosService.crearCuenta(cuenta));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listarTodas() {
        return ResponseEntity.ok(cuentasAhorrosService.obtenerTodas());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(cuentasAhorrosService.obtenerPorId(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Integer id, @RequestBody CuentasAhorros cuenta) {
        try {
            return ResponseEntity.ok(cuentasAhorrosService.actualizarCuenta(id, cuenta));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Integer id) {
        try {
            cuentasAhorrosService.eliminarLogico(id);
            return ResponseEntity.ok("Cuenta de ahorros inactivada correctamente.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}