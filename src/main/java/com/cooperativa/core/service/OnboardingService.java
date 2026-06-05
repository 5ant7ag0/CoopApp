package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.EmpresaOnboardingDTO;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.model.PlanCuentas;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.repository.PlanCuentasRepository;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import com.cooperativa.core.security.EncryptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para automatizar el onboarding de nuevas cooperativas (empresas).
 * Registra en una sola transaccion la cooperativa, su administrador inicial y su plan de cuentas base.
 */
@Service
public class OnboardingService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private PlanCuentasRepository planCuentasRepository;

    @Autowired
    private EncryptionService encryptionService;

    /**
     * Registra una nueva empresa, su administrador y su plan de cuentas de forma atomica.
     * Implementa la tecnica de conmutacion de tenant para evitar que @PrePersist de BaseEntity
     * asocie los registros del nuevo tenant con el ID del SuperAdmin.
     */
    @Transactional(rollbackFor = Exception.class)
    public Empresa onboard(EmpresaOnboardingDTO dto) {
        Empresa empresa = dto.getEmpresa();
        UsuariosAdmin admin = dto.getAdmin();

        // 1. Validar que la empresa no exista previamente por RUC o Codigo SEPS
        if (empresaRepository.findByRuc(empresa.getRuc()).isPresent()) {
            throw new IllegalArgumentException("Error: Ya existe una empresa registrada con el RUC '" + empresa.getRuc() + "'.");
        }
        if (empresaRepository.findByCodigoSeps(empresa.getCodigoSeps()).isPresent()) {
            throw new IllegalArgumentException("Error: Ya existe una empresa registrada con el codigo SEPS '" + empresa.getCodigoSeps() + "'.");
        }

        // 2. Guardar la nueva Empresa (no hereda de BaseEntity, por lo tanto no le afecta el tenant activo)
        Empresa savedEmpresa = empresaRepository.save(empresa);

        // 3. Conmutacion Temporal del TenantContext para guardar entidades hijas (Admin y Plan de Cuentas)
        Integer originalTenant = TenantContext.getCurrentTenant();
        try {
            // Establecemos temporalmente el TenantContext al nuevo ID de empresa
            TenantContext.setCurrentTenant(savedEmpresa.getId());

            // 4. Preparar y persistir el Administrador Inicial
            if (admin.getUsername() == null || admin.getUsername().trim().isEmpty()) {
                throw new IllegalArgumentException("Error: El nombre de usuario del administrador no puede estar vacio.");
            }
            if (admin.getPasswordHash() == null || admin.getPasswordHash().trim().isEmpty()) {
                throw new IllegalArgumentException("Error: La contrasena del administrador no puede estar vacia.");
            }

            // Encriptamos la contrasena en texto plano que viene en el DTO
            String plainPassword = admin.getPasswordHash();
            admin.setPasswordHash(encryptionService.hashPassword(plainPassword));
            admin.setEmpresaId(savedEmpresa.getId());
            
            // Forzar rol administrativo base para el onboarding (por ejemplo GERENTE_GENERAL)
            if (admin.getRol() == null || admin.getRol().trim().isEmpty()) {
                admin.setRol("GERENTE_GENERAL");
            }
            admin.setEstado("ACTIVO");

            usuarioAdminRepository.save(admin);

            // 5. Inyectar el Plan de Cuentas base de 18 cuentas contables por defecto
            sembrarPlanCuentasDefecto(savedEmpresa.getId());

        } finally {
            // Restauramos obligatoriamente el contexto original del SuperAdmin para evitar fugas de contexto
            TenantContext.setCurrentTenant(originalTenant);
        }

        return savedEmpresa;
    }

    /**
     * Registra las 18 cuentas contables minimas requeridas para el funcionamiento
     * de los modulos core y de creditos/ahorros.
     */
    private void sembrarPlanCuentasDefecto(Integer empresaId) {
        String[][] cuentasDefecto = {
            // {codigo, nombre, tipo, esMovimiento}
            {"1", "ACTIVOS", "ACTIVO", "false"},
            {"1.1", "FONDOS DISPONIBLES", "ACTIVO", "false"},
            {"1.1.01", "CAJA", "ACTIVO", "false"},
            {"1.1.01.05", "Caja General Ventanilla", "ACTIVO", "true"},
            {"1.4", "CARTERA DE CREDITOS", "ACTIVO", "false"},
            {"1.4.01", "Cartera de Créditos por Desembolsar", "ACTIVO", "true"},
            {"2", "PASIVOS", "PASIVO", "false"},
            {"2.1", "OBLIGACIONES CON EL PUBLICO", "PASIVO", "false"},
            {"2.1.01", "DEPOSITOS A LA VISTA", "PASIVO", "false"},
            {"2.1.01.05", "Cuentas de Ahorros de Socios", "PASIVO", "true"},
            {"3", "PATRIMONIO", "PATRIMONIO", "false"},
            {"3.1", "CAPITAL SOCIAL", "PATRIMONIO", "false"},
            {"3.1.01", "Capital Social Numerario", "PATRIMONIO", "false"},
            {"3.1.01.05", "Aportaciones Obligatorias de Socios", "PATRIMONIO", "true"},
            {"5", "INGRESOS", "INGRESO", "false"},
            {"5.1", "INGRESOS FINANCIEROS", "INGRESO", "false"},
            {"5.1.01", "INGRESOS POR INTERESES DE CARTERA", "INGRESO", "false"},
            {"5.1.01.05", "Intereses Cartera de Créditos Vigente", "INGRESO", "true"}
        };

        for (String[] def : cuentasDefecto) {
            PlanCuentas cuenta = new PlanCuentas();
            cuenta.setEmpresaId(empresaId);
            cuenta.setCodigoContable(def[0]);
            cuenta.setNombreCuenta(def[1]);
            cuenta.setTipoCuenta(def[2]);
            cuenta.setEsMovimiento(Boolean.parseBoolean(def[3]));
            planCuentasRepository.save(cuenta);
        }
    }
}
