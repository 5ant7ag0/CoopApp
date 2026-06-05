package com.cooperativa.core.controller;

import com.cooperativa.core.dto.EmpresaOnboardingDTO;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.security.SuperAdminOnly;
import com.cooperativa.core.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador de Administracion Global.
 * Protegido exclusivamente para el rol SUPER_ADMIN_SAAS.
 */
@RestController
@RequestMapping("/superadmin")
@SuperAdminOnly
@CrossOrigin(origins = "*")
public class SuperAdminController {

    @Autowired
    private OnboardingService onboardingService;

    /**
     * Endpoint para realizar el onboarding automatico de una nueva cooperativa.
     * Registra la empresa, su usuario administrador y siembra su plan de cuentas.
     * URL: http://localhost:8080/api/v1/superadmin/onboard
     */
    @PostMapping("/onboard")
    public ResponseEntity<?> onboardEmpresa(@Valid @RequestBody EmpresaOnboardingDTO requestDTO) {
        try {
            Empresa nuevaEmpresa = onboardingService.onboard(requestDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevaEmpresa);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno durante el proceso de onboarding: " + e.getMessage());
        }
    }
}
