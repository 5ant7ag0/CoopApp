package com.cooperativa.core.controller;

import com.cooperativa.core.dto.ProductoAhorroRequestDTO;
import com.cooperativa.core.model.ProductoAhorro;
import com.cooperativa.core.security.RequiresRoles;
import com.cooperativa.core.service.ProductoAhorroService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/productos-ahorro")

public class ProductoAhorroController {

    @Autowired
    private ProductoAhorroService productoAhorroService;

    @GetMapping
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "CONTADOR", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO", "CAJERO", "AUDITOR_INTERNO"})
    public ResponseEntity<List<ProductoAhorro>> listar() {
        return ResponseEntity.ok(productoAhorroService.obtenerTodos());
    }

    @GetMapping("/activos")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "CONTADOR", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO", "CAJERO", "AUDITOR_INTERNO", "SOCIO"})
    public ResponseEntity<List<ProductoAhorro>> listarActivos() {
        return ResponseEntity.ok(productoAhorroService.obtenerActivos());
    }

    @GetMapping("/{id}")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "CONTADOR", "SUPER_ADMIN_SAAS", "OFICIAL_DE_CREDITO", "CAJERO", "AUDITOR_INTERNO", "SOCIO"})
    public ResponseEntity<ProductoAhorro> obtenerPorId(@PathVariable Integer id) {
        try {
            return ResponseEntity.ok(productoAhorroService.obtenerPorId(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> crear(@Valid @RequestBody ProductoAhorroRequestDTO dto, HttpServletRequest request) {
        String authUsername = (String) request.getAttribute("authUsername");
        try {
            ProductoAhorro nuevo = productoAhorroService.crear(dto, authUsername);
            return ResponseEntity.ok(nuevo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> actualizar(
            @PathVariable Integer id,
            @Valid @RequestBody ProductoAhorroRequestDTO dto,
            HttpServletRequest request) {
        String authUsername = (String) request.getAttribute("authUsername");
        try {
            ProductoAhorro actualizado = productoAhorroService.actualizar(id, dto, authUsername);
            return ResponseEntity.ok(actualizado);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @RequiresRoles({"ADMINISTRADOR", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
    public ResponseEntity<?> eliminar(@PathVariable Integer id, HttpServletRequest request) {
        String authUsername = (String) request.getAttribute("authUsername");
        try {
            productoAhorroService.eliminarLogico(id, authUsername);
            return ResponseEntity.ok("Producto de ahorro inactivado con éxito.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
