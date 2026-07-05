package com.cooperativa.core.service;

import com.cooperativa.core.dto.EmpresaOnboardingDTO;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.exception.EmpresaDuplicadaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para automatizar el onboarding de nuevas cooperativas (empresas).
 * Registra la cooperativa y delega el sembrado del plan de cuentas y administrador inicial
 * a TenantSeedingService utilizando JdbcTemplate para evitar conflictos de multi-tenancy.
 */
@Service
public class OnboardingService {

    @Autowired
    private EmpresaRepository empresaRepository;

    @Autowired
    private TenantSeedingService tenantSeedingService;

    /**
     * Registra una nueva empresa y delega el sembrado de datos iniciales.
     * Toda la operación se realiza de forma atómica dentro de una sola transacción.
     */
    @Transactional(rollbackFor = Exception.class)
    public Empresa onboard(EmpresaOnboardingDTO dto) {
        Empresa empresa = dto.getEmpresa();
        UsuariosAdmin admin = dto.getAdmin();

        // 1. Validar que la empresa no exista previamente por RUC o Codigo SEPS
        if (empresaRepository.existsByRucRaw(empresa.getRuc())) {
            throw new EmpresaDuplicadaException("Error: Ya existe una empresa registrada con el RUC '" + empresa.getRuc() + "'.");
        }
        if (empresaRepository.existsByCodigoSepsRaw(empresa.getCodigoSeps())) {
            throw new EmpresaDuplicadaException("Error: Ya existe una empresa registrada con el codigo SEPS '" + empresa.getCodigoSeps() + "'.");
        }

        // 2. Guardar la nueva Empresa
        Empresa savedEmpresa = empresaRepository.save(empresa);

        // 3. Sembrar el Administrador y el Plan de Cuentas usando JdbcTemplate
        tenantSeedingService.seedTenantData(savedEmpresa, admin);

        // Desvincular referencias de PlanCuentas para la serialización segura bajo contexto SuperAdmin (evita filtros de tenant)
        savedEmpresa.setCuentaContableCaja(null);
        savedEmpresa.setCuentaContableCartera(null);
        savedEmpresa.setCuentaContableObligaciones(null);
        savedEmpresa.setCuentaContableAportaciones(null);
        savedEmpresa.setCuentaContableSeguro(null);
        savedEmpresa.setCuentaContablePapeleria(null);
        savedEmpresa.setCuentaContableGastosIntereses(null);
        savedEmpresa.setCuentaContableIngresosIntereses(null);
        savedEmpresa.setCuentaContableMora(null);

        return savedEmpresa;
    }
}
