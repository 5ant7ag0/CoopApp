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

    @Autowired
    private com.cooperativa.core.repository.PlanCuentasRepository planCuentasRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

    @Autowired
    private com.cooperativa.core.repository.UsuarioAdminRepository usuarioAdminRepository;

    // ACTUALIZAR LA CONFIGURACIÓN DE LA COOPERATIVA ACTIVA (tenant)
    @Transactional
    public Empresa actualizarMiEmpresa(Empresa datosNuevos, String username, String ip, String dispositivo) {
        Empresa empresaExistente = obtenerMiEmpresa();
        Integer tenantId = TenantContext.getCurrentTenant();

        // Registrar cambios para auditoría
        java.util.Map<String, Object> valorAnterior = new java.util.HashMap<>();
        java.util.Map<String, Object> valorNuevo = new java.util.HashMap<>();

        // Helper para comparar y asignar campos String
        compararYAsignarString("razonSocial", empresaExistente.getRazonSocial(), datosNuevos.getRazonSocial(), empresaExistente::setRazonSocial, valorAnterior, valorNuevo);
        compararYAsignarString("nombreComercial", empresaExistente.getNombreComercial(), datosNuevos.getNombreComercial(), empresaExistente::setNombreComercial, valorAnterior, valorNuevo);
        compararYAsignarString("representanteLegal", empresaExistente.getRepresentanteLegal(), datosNuevos.getRepresentanteLegal(), empresaExistente::setRepresentanteLegal, valorAnterior, valorNuevo);
        compararYAsignarString("cedulaRepresentante", empresaExistente.getCedulaRepresentante(), datosNuevos.getCedulaRepresentante(), empresaExistente::setCedulaRepresentante, valorAnterior, valorNuevo);
        compararYAsignarString("logoUrl", empresaExistente.getLogoUrl(), datosNuevos.getLogoUrl(), empresaExistente::setLogoUrl, valorAnterior, valorNuevo);
        
        compararYAsignarString("direccion", empresaExistente.getDireccion(), datosNuevos.getDireccion(), empresaExistente::setDireccion, valorAnterior, valorNuevo);
        compararYAsignarString("telefono", empresaExistente.getTelefono(), datosNuevos.getTelefono(), empresaExistente::setTelefono, valorAnterior, valorNuevo);
        compararYAsignarString("siglas", empresaExistente.getSiglas(), datosNuevos.getSiglas(), empresaExistente::setSiglas, valorAnterior, valorNuevo);
        compararYAsignarString("segmentoSeps", empresaExistente.getSegmentoSeps(), datosNuevos.getSegmentoSeps(), empresaExistente::setSegmentoSeps, valorAnterior, valorNuevo);
        compararYAsignarString("resolucionSeps", empresaExistente.getResolucionSeps(), datosNuevos.getResolucionSeps(), empresaExistente::setResolucionSeps, valorAnterior, valorNuevo);
        compararYAsignarString("correoInstitucional", empresaExistente.getCorreoInstitucional(), datosNuevos.getCorreoInstitucional(), empresaExistente::setCorreoInstitucional, valorAnterior, valorNuevo);
        compararYAsignarString("provincia", empresaExistente.getProvincia(), datosNuevos.getProvincia(), empresaExistente::setProvincia, valorAnterior, valorNuevo);
        compararYAsignarString("canton", empresaExistente.getCanton(), datosNuevos.getCanton(), empresaExistente::setCanton, valorAnterior, valorNuevo);

        // Helper para comparar y asignar BigDecimal
        compararYAsignarBigDecimal("saldoMinimoApertura", empresaExistente.getSaldoMinimoApertura(), datosNuevos.getSaldoMinimoApertura(), empresaExistente::setSaldoMinimoApertura, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("montoMinimoCredito", empresaExistente.getMontoMinimoCredito(), datosNuevos.getMontoMinimoCredito(), empresaExistente::setMontoMinimoCredito, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("montoMaximoCredito", empresaExistente.getMontoMaximoCredito(), datosNuevos.getMontoMaximoCredito(), empresaExistente::setMontoMaximoCredito, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("tasaInteresAnual", empresaExistente.getTasaInteresAnual(), datosNuevos.getTasaInteresAnual(), empresaExistente::setTasaInteresAnual, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("tasaInteresMora", empresaExistente.getTasaInteresMora(), datosNuevos.getTasaInteresMora(), empresaExistente::setTasaInteresMora, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("costoTramite", empresaExistente.getCostoTramite(), datosNuevos.getCostoTramite(), empresaExistente::setCostoTramite, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("porcentajeSeguroDesgravamen", empresaExistente.getPorcentajeSeguroDesgravamen(), datosNuevos.getPorcentajeSeguroDesgravamen(), empresaExistente::setPorcentajeSeguroDesgravamen, valorAnterior, valorNuevo);
        compararYAsignarBigDecimal("cuotaAportacionMensual", empresaExistente.getCuotaAportacionMensual(), datosNuevos.getCuotaAportacionMensual(), empresaExistente::setCuotaAportacionMensual, valorAnterior, valorNuevo);

        // Enlaces Contables
        actualizarCuentaContable("cuentaContableCartera", empresaExistente.getCuentaContableCartera(), datosNuevos.getCuentaContableCartera(), empresaExistente::setCuentaContableCartera, tenantId, valorAnterior, valorNuevo);
        actualizarCuentaContable("cuentaContableSeguro", empresaExistente.getCuentaContableSeguro(), datosNuevos.getCuentaContableSeguro(), empresaExistente::setCuentaContableSeguro, tenantId, valorAnterior, valorNuevo);
        actualizarCuentaContable("cuentaContablePapeleria", empresaExistente.getCuentaContablePapeleria(), datosNuevos.getCuentaContablePapeleria(), empresaExistente::setCuentaContablePapeleria, tenantId, valorAnterior, valorNuevo);

        Empresa guardada = empresaRepository.save(empresaExistente);

        // Si hubo cambios, registramos auditoría
        if (!valorNuevo.isEmpty()) {
            com.cooperativa.core.model.LogsAuditoria log = new com.cooperativa.core.model.LogsAuditoria();
            Integer usuarioId = resolverUsuarioId(username, tenantId);
            log.setUsuarioAdminId(usuarioId);
            log.setAccion("ACTUALIZAR_PARAMETROS");
            log.setTablaAfectada("empresas");
            log.setRegistroId(guardada.getId());
            log.setValorAnterior(valorAnterior);
            log.setValorNuevo(valorNuevo);
            log.setDireccionIp(ip);
            log.setDispositivoInfo(dispositivo);
            logsAuditoriaService.registrarLog(log);
        }

        return guardada;
    }

    private Integer resolverUsuarioId(String username, Integer tenantId) {
        if (username == null) return null;
        return usuarioAdminRepository.findByUsernameAndEmpresaId(username, tenantId)
                .map(u -> u.getId())
                .orElse(null);
    }

    private void compararYAsignarString(String nombreCampo, String actual, String nuevo, java.util.function.Consumer<String> setter, java.util.Map<String, Object> valorAnterior, java.util.Map<String, Object> valorNuevo) {
        if (nuevo != null && !nuevo.equals(actual)) {
            valorAnterior.put(nombreCampo, actual);
            valorNuevo.put(nombreCampo, nuevo);
            setter.accept(nuevo);
        }
    }

    private void compararYAsignarBigDecimal(String nombreCampo, java.math.BigDecimal actual, java.math.BigDecimal nuevo, java.util.function.Consumer<java.math.BigDecimal> setter, java.util.Map<String, Object> valorAnterior, java.util.Map<String, Object> valorNuevo) {
        if (nuevo != null && (actual == null || nuevo.compareTo(actual) != 0)) {
            valorAnterior.put(nombreCampo, actual != null ? actual.toString() : null);
            valorNuevo.put(nombreCampo, nuevo.toString());
            setter.accept(nuevo);
        }
    }

    private void actualizarCuentaContable(String nombreCampo, com.cooperativa.core.model.PlanCuentas actual, com.cooperativa.core.model.PlanCuentas nuevo, java.util.function.Consumer<com.cooperativa.core.model.PlanCuentas> setter, Integer tenantId, java.util.Map<String, Object> valorAnterior, java.util.Map<String, Object> valorNuevo) {
        Integer idActual = actual != null ? actual.getId() : null;
        Integer idNuevo = nuevo != null ? nuevo.getId() : null;

        if (idNuevo != null && !idNuevo.equals(idActual)) {
            com.cooperativa.core.model.PlanCuentas pc = planCuentasRepository.findById(idNuevo)
                    .orElseThrow(() -> new IllegalArgumentException("Cuenta contable no encontrada con ID: " + idNuevo));
            if (!pc.getEmpresaId().equals(tenantId)) {
                throw new IllegalArgumentException("Error de Seguridad: La cuenta contable no pertenece a esta cooperativa.");
            }
            if (!Boolean.TRUE.equals(pc.getEsMovimiento())) {
                throw new IllegalArgumentException("Error Contable: Solo se pueden asignar cuentas contables de nivel detalle (movimiento).");
            }
            valorAnterior.put(nombreCampo, actual != null ? actual.getCodigoContable() + " - " + actual.getNombreCuenta() : null);
            valorNuevo.put(nombreCampo, pc.getCodigoContable() + " - " + pc.getNombreCuenta());
            setter.accept(pc);
        } else if (nuevo == null && idActual != null) {
            valorAnterior.put(nombreCampo, actual.getCodigoContable() + " - " + actual.getNombreCuenta());
            valorNuevo.put(nombreCampo, null);
            setter.accept(null);
        }
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
