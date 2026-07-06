package com.cooperativa.core.controller;

import com.cooperativa.core.dto.CajaVentanillaDTO;
import com.cooperativa.core.security.RequiresRoles;
import com.cooperativa.core.service.CajasVentanillaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cajas-ventanilla")
@RequiredArgsConstructor

public class CajasVentanillaController {

    private final CajasVentanillaService cajasService;

    @GetMapping
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL", "CAJERO"})
    public ResponseEntity<List<CajaVentanillaDTO>> listarCajas() {
        return ResponseEntity.ok(cajasService.listarCajas());
    }

    @PostMapping
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL"})
    public ResponseEntity<CajaVentanillaDTO> crearCaja(@RequestBody CajaVentanillaDTO dto) {
        return ResponseEntity.ok(cajasService.crearCaja(dto));
    }
    
    @PutMapping("/{id}")
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL"})
    public ResponseEntity<CajaVentanillaDTO> actualizarCaja(@PathVariable Integer id, @RequestBody CajaVentanillaDTO dto) {
        return ResponseEntity.ok(cajasService.actualizarCaja(id, dto));
    }
}
