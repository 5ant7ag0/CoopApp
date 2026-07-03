package com.cooperativa.core.controller;

import com.cooperativa.core.dto.ProductoCreditoDTO;
import com.cooperativa.core.model.ProductoCredito;
import com.cooperativa.core.security.RequiresRoles;
import com.cooperativa.core.service.ProductoCreditoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productos-credito")
@CrossOrigin(origins = "*")
public class ProductoCreditoController {

    @Autowired
    private ProductoCreditoService productoCreditoService;

    @GetMapping
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "CONTADOR", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO"})
    public ResponseEntity<List<ProductoCredito>> listarTodos() {
        return ResponseEntity.ok(productoCreditoService.listarTodos());
    }

    @GetMapping("/activos")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "CONTADOR", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO", "SOCIO", "CAJERO"})
    public ResponseEntity<List<ProductoCredito>> listarActivos() {
        return ResponseEntity.ok(productoCreditoService.listarActivos());
    }

    @GetMapping("/{id}")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "CONTADOR", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO"})
    public ResponseEntity<ProductoCredito> obtenerPorId(@PathVariable Integer id) {
        return ResponseEntity.ok(productoCreditoService.obtenerPorId(id));
    }

    @PostMapping
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<ProductoCredito> crear(@RequestBody ProductoCreditoDTO dto) {
        return ResponseEntity.ok(productoCreditoService.crear(dto));
    }

    @PutMapping("/{id}")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<ProductoCredito> actualizar(@PathVariable Integer id, @RequestBody ProductoCreditoDTO dto) {
        return ResponseEntity.ok(productoCreditoService.actualizar(id, dto));
    }
}
