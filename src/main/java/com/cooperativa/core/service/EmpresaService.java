package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.repository.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmpresaService {

    @Autowired
    private EmpresaRepository empresaRepository;

    // LEER LOS DATOS DE LA COOPERATIVA ACTIVA (tenant)
    public Empresa obtenerMiEmpresa() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalArgumentException("Error: No se ha especificado el encabezado X-Tenant-ID.");
        }
        return empresaRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error Crítico: Institución financiera no encontrada en la base de datos."));
    }

    // ACTUALIZAR LA CONFIGURACIÓN DE LA COOPERATIVA ACTIVA (tenant)
    @Transactional
    public Empresa actualizarMiEmpresa(Empresa datosNuevos) {
        Empresa empresaExistente = obtenerMiEmpresa();

        // Solo permitimos actualizar campos informativos y legales.
        // El RUC o el ID no se modifican para no romper la integridad de la base de datos.
        empresaExistente.setRazonSocial(datosNuevos.getRazonSocial());
        empresaExistente.setNombreComercial(datosNuevos.getNombreComercial());
        empresaExistente.setRepresentanteLegal(datosNuevos.getRepresentanteLegal());
        empresaExistente.setCedulaRepresentante(datosNuevos.getCedulaRepresentante());
        if (datosNuevos.getLogoUrl() != null) {
            empresaExistente.setLogoUrl(datosNuevos.getLogoUrl());
        }
        return empresaRepository.save(empresaExistente);
    }

    // --- MÉTODOS CRUD ADICIONALES PARA ADMINISTRACIÓN GLOBAL ---

    // CREAR NUEVA EMPRESA
    @Transactional
    public Empresa crearEmpresa(Empresa nuevaEmpresa) {
        if (empresaRepository.findByRuc(nuevaEmpresa.getRuc()).isPresent()) {
            throw new IllegalArgumentException("Error: Ya existe una empresa registrada con el RUC " + nuevaEmpresa.getRuc());
        }
        if (empresaRepository.findByCodigoSeps(nuevaEmpresa.getCodigoSeps()).isPresent()) {
            throw new IllegalArgumentException("Error: Ya existe una empresa registrada con el código SEPS " + nuevaEmpresa.getCodigoSeps());
        }
        return empresaRepository.save(nuevaEmpresa);
    }

    // OBTENER TODAS LAS EMPRESAS
    public List<Empresa> obtenerTodas() {
        return empresaRepository.findAll();
    }

    // OBTENER EMPRESA POR ID
    public Empresa obtenerPorId(Integer id) {
        return empresaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Error: Empresa no encontrada con el ID: " + id));
    }

    // ACTUALIZAR EMPRESA POR ID
    @Transactional
    public Empresa actualizarEmpresa(Integer id, Empresa datosNuevos) {
        Empresa empresaExistente = obtenerPorId(id);

        // Validar RUC único si cambia
        if (datosNuevos.getRuc() != null && !datosNuevos.getRuc().equals(empresaExistente.getRuc())) {
            if (empresaRepository.findByRuc(datosNuevos.getRuc()).isPresent()) {
                throw new IllegalArgumentException("Error: Ya existe otra empresa registrada con el RUC " + datosNuevos.getRuc());
            }
            empresaExistente.setRuc(datosNuevos.getRuc());
        }

        // Validar SEPS único si cambia
        if (datosNuevos.getCodigoSeps() != null && !datosNuevos.getCodigoSeps().equals(empresaExistente.getCodigoSeps())) {
            if (empresaRepository.findByCodigoSeps(datosNuevos.getCodigoSeps()).isPresent()) {
                throw new IllegalArgumentException("Error: Ya existe otra empresa registrada con el código SEPS " + datosNuevos.getCodigoSeps());
            }
            empresaExistente.setCodigoSeps(datosNuevos.getCodigoSeps());
        }

        if (datosNuevos.getRazonSocial() != null) {
            empresaExistente.setRazonSocial(datosNuevos.getRazonSocial());
        }
        if (datosNuevos.getNombreComercial() != null) {
            empresaExistente.setNombreComercial(datosNuevos.getNombreComercial());
        }
        if (datosNuevos.getRepresentanteLegal() != null) {
            empresaExistente.setRepresentanteLegal(datosNuevos.getRepresentanteLegal());
        }
        if (datosNuevos.getCedulaRepresentante() != null) {
            empresaExistente.setCedulaRepresentante(datosNuevos.getCedulaRepresentante());
        }
        if (datosNuevos.getLogoUrl() != null) {
            empresaExistente.setLogoUrl(datosNuevos.getLogoUrl());
        }
        if (datosNuevos.getMoneda() != null) {
            empresaExistente.setMoneda(datosNuevos.getMoneda());
        }
        if (datosNuevos.getEstado() != null) {
            empresaExistente.setEstado(datosNuevos.getEstado());
        }

        return empresaRepository.save(empresaExistente);
    }

    // ELIMINACIÓN LÓGICA DE EMPRESA (Inactivación por estado)
    @Transactional
    public void eliminarEmpresa(Integer id) {
        Empresa empresa = obtenerPorId(id);
        empresa.setEstado("INACTIVO");
        empresaRepository.save(empresa);
    }
}
