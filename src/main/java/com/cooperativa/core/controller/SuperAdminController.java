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
import com.cooperativa.core.dto.TenantListDTO;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.repository.SocioRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import com.cooperativa.core.security.TenantStateCache;
import com.cooperativa.core.model.TenantEstado;
import com.cooperativa.core.dto.TenantDetailDTO;
import com.cooperativa.core.dto.TenantUpdateGeneralDTO;
import com.cooperativa.core.dto.TenantUpdateLimitsDTO;
import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;

import com.cooperativa.core.model.UsuariosAdmin;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador de Administracion Global.
 * Protegido exclusivamente para el rol SUPER_ADMIN_SAAS.
 */
@RestController
@RequestMapping("/superadmin")
@SuperAdminOnly

public class SuperAdminController {

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private TenantStateCache tenantStateCache;
    
    @Autowired
    private AuthService authService;

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
        } catch (com.cooperativa.core.exception.EmpresaDuplicadaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String msg = "Error: Conflicto de integridad de datos en el sistema.";
            if (e.getMostSpecificCause() != null && e.getMostSpecificCause().getMessage() != null) {
                String causeMsg = e.getMostSpecificCause().getMessage().toLowerCase();
                if (causeMsg.contains("ruc")) {
                    msg = "Error: Ya existe una empresa registrada con ese RUC.";
                } else if (causeMsg.contains("codigo_seps")) {
                    msg = "Error: Ya existe una empresa registrada con ese código SEPS.";
                } else if (causeMsg.contains("identificacion")) {
                    msg = "Error: La identificación (cédula) del representante ya está registrada para otro administrador.";
                } else if (causeMsg.contains("username") || causeMsg.contains("uk_empresa_username")) {
                    msg = "Error: El nombre de usuario del administrador ya está registrado.";
                } else if (causeMsg.contains("correo")) {
                    msg = "Error: El correo electrónico del representante ya está registrado para otro administrador.";
                }
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).body(msg);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno durante el proceso de onboarding: " + e.getMessage());
        }
    }

    @GetMapping("/tenants/check-ruc")
    public ResponseEntity<?> verificarRuc(@RequestParam String ruc) {
        boolean exists = empresaRepository.existsByRucRaw(ruc);
        return ResponseEntity.ok(java.util.Map.of("exists", exists));
    }

    @GetMapping("/tenants/check-seps")
    public ResponseEntity<?> verificarCodigoSeps(@RequestParam String codigoSeps) {
        boolean exists = empresaRepository.existsByCodigoSepsRaw(codigoSeps);
        return ResponseEntity.ok(java.util.Map.of("exists", exists));
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<TenantListDTO>> listarTenants() {
        List<Object[]> results = empresaRepository.findAllTenantsRaw();
        List<TenantListDTO> tenants = results.stream().map(row -> {
            TenantListDTO dto = new TenantListDTO();
            dto.setId(((Number) row[0]).intValue());
            dto.setRuc((String) row[1]);
            dto.setRazonSocial((String) row[2]);
            dto.setNombreComercial((String) row[3]);
            dto.setRepresentanteLegal((String) row[4]);
            dto.setCorreoInstitucional((String) row[5]);
            
            String estadoStr = (String) row[6];
            if (estadoStr != null) {
                dto.setEstado(TenantEstado.valueOf(estadoStr));
            }
            
            dto.setLimiteUsuariosAdmin(row[7] != null ? ((Number) row[7]).intValue() : 0);
            dto.setLimiteSocios(row[8] != null ? ((Number) row[8]).intValue() : 0);
            
            if (row[9] != null) {
                if (row[9] instanceof java.sql.Timestamp) {
                    dto.setCreatedAt(((java.sql.Timestamp) row[9]).toLocalDateTime());
                } else if (row[9] instanceof java.time.LocalDateTime) {
                    dto.setCreatedAt((java.time.LocalDateTime) row[9]);
                }
            }
            
            dto.setTotalUsuarios(usuarioAdminRepository.countByEmpresaId(dto.getId()));
            dto.setTotalSocios(socioRepository.countByEmpresaId(dto.getId()));
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(tenants);
    }

    @PostMapping("/tenants/{id}/suspend")
    public ResponseEntity<?> suspenderTenant(@PathVariable Integer id) {
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
        
        empresa.setEstado(TenantEstado.SUSPENDIDO);
        empresaRepository.save(empresa);

        // Kill Switch: Invalidar tokens en caliente
        tenantStateCache.suspendTenant(id);

        return ResponseEntity.ok("Tenant suspendido exitosamente.");
    }

    @PostMapping("/tenants/{id}/reactivate")
    public ResponseEntity<?> reactivarTenant(@PathVariable Integer id) {
        Empresa empresa = empresaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
        
        empresa.setEstado(TenantEstado.ACTIVO);
        empresaRepository.save(empresa);

        // Remover de la caché de suspensiones
        tenantStateCache.reactivateTenant(id);

        return ResponseEntity.ok("Tenant reactivado exitosamente.");
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<?> getTenantDetails(@PathVariable Integer id) {
        TenantContext.setCurrentTenant(id);
        try {
            Empresa empresa = empresaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
            TenantDetailDTO dto = new TenantDetailDTO();
            dto.setId(empresa.getId());
            dto.setRuc(empresa.getRuc());
            dto.setRazonSocial(empresa.getRazonSocial());
            dto.setNombreComercial(empresa.getNombreComercial());
            dto.setRepresentanteLegal(empresa.getRepresentanteLegal());
            dto.setCedulaRepresentante(empresa.getCedulaRepresentante());
            dto.setCorreoInstitucional(empresa.getCorreoInstitucional());
            dto.setDireccion(empresa.getDireccion());
            dto.setTelefono(empresa.getTelefono());
            dto.setCodigoSeps(empresa.getCodigoSeps());
            dto.setSegmentoSeps(empresa.getSegmentoSeps());
            dto.setEstado(empresa.getEstado());
            dto.setLimiteUsuariosAdmin(empresa.getLimiteUsuariosAdmin());
            dto.setLimiteSocios(empresa.getLimiteSocios());
            dto.setTotalUsuarios(usuarioAdminRepository.countByEmpresaId(id));
            dto.setTotalSocios(socioRepository.countByEmpresaId(id));
            dto.setCreatedAt(empresa.getCreatedAt());
            dto.setLogoUrl(empresa.getLogoUrl());
            dto.setSiglas(empresa.getSiglas());
            
            Optional<UsuariosAdmin> gerenteOpt = usuarioAdminRepository.findGerenteGeneralRaw(id);
            if (gerenteOpt.isPresent() && gerenteOpt.get().getCorreo() != null && !gerenteOpt.get().getCorreo().trim().isEmpty()) {
                dto.setCorreoGerente(enmascararCorreo(gerenteOpt.get().getCorreo()));
            } else if (empresa.getCorreoGerente() != null && !empresa.getCorreoGerente().trim().isEmpty()) {
                dto.setCorreoGerente(enmascararCorreo(empresa.getCorreoGerente()));
            } else {
                dto.setCorreoGerente("No registrado");
            }
            
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @PutMapping("/tenants/{id}/general")
    public ResponseEntity<?> updateTenantGeneral(@PathVariable Integer id, @Valid @RequestBody TenantUpdateGeneralDTO dto) {
        TenantContext.setCurrentTenant(id);
        try {
            Empresa empresa = empresaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
            empresa.setRuc(dto.getRuc());
            empresa.setRazonSocial(dto.getRazonSocial());
            empresa.setNombreComercial(dto.getNombreComercial());
            empresa.setRepresentanteLegal(dto.getRepresentanteLegal());
            empresa.setCedulaRepresentante(dto.getCedulaRepresentante());
            empresa.setCorreoInstitucional(dto.getCorreoInstitucional());
            empresa.setDireccion(dto.getDireccion());
            empresa.setTelefono(dto.getTelefono());
            empresa.setCodigoSeps(dto.getCodigoSeps());
            empresa.setSegmentoSeps(dto.getSegmentoSeps());
            empresaRepository.save(empresa);
            return ResponseEntity.ok("Datos generales actualizados");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @PutMapping("/tenants/{id}/limits")
    public ResponseEntity<?> updateTenantLimits(@PathVariable Integer id, @Valid @RequestBody TenantUpdateLimitsDTO dto) {
        TenantContext.setCurrentTenant(id);
        try {
            Empresa empresa = empresaRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado"));
            empresa.setLimiteUsuariosAdmin(dto.getLimiteUsuariosAdmin());
            empresa.setLimiteSocios(dto.getLimiteSocios());
            empresaRepository.save(empresa);
            return ResponseEntity.ok("Cuotas y límites actualizados");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping("/tenants/{id}/reset-manager")
    public ResponseEntity<?> resetManagerPassword(@PathVariable Integer id, HttpServletRequest request) {
        TenantContext.setCurrentTenant(id);
        try {
            String ip = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String maskedEmail = authService.enviarEnlaceRestablecimientoAdmin(id, ip, userAgent);
            return ResponseEntity.ok("Enlace seguro de restablecimiento de contraseña enviado a: " + maskedEmail);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private String enmascararCorreo(String correo) {
        if (correo == null || !correo.contains("@")) return correo;
        String[] parts = correo.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) {
            return name + "***@" + domain;
        }
        return name.substring(0, 2) + "***@" + domain;
    }
}
