package com.cooperativa.core.controller;

import com.cooperativa.core.dto.AgenciaDTO;
import com.cooperativa.core.security.RequiresRoles;
import com.cooperativa.core.service.AgenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agencias")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AgenciaController {

    private final AgenciaService agenciaService;

    @GetMapping
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL", "OFICIAL_DE_CREDITO", "CAJERO", "CONTADOR"})
    public ResponseEntity<List<AgenciaDTO>> listarAgencias() {
        return ResponseEntity.ok(agenciaService.listarAgencias());
    }

    @PostMapping
    @RequiresRoles({"SUPER_ADMIN_SAAS", "GERENTE_GENERAL"})
    public ResponseEntity<AgenciaDTO> crearAgencia(@RequestBody AgenciaDTO dto) {
        return ResponseEntity.ok(agenciaService.crearAgencia(dto));
    }
}
