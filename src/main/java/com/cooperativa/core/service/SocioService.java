package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.Socio;
import com.cooperativa.core.repository.SocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class SocioService {

    @Autowired
    private SocioRepository socioRepository;

    // CREAR UN NUEVO SOCIO
    @Transactional
    public Socio crearSocio(Socio socio) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede guardar datos sin un X-Tenant-ID definido.");
        }

        // Regla: Evitar duplicados de cédula/RUC en la misma cooperativa
        if (socioRepository.existsByIdentificacionAndEmpresaId(socio.getIdentificacion(), tenantId)) {
            throw new IllegalStateException("Error: Ya existe un socio registrado con la identificacion " + socio.getIdentificacion() + " en esta cooperativa.");
        }

        return socioRepository.save(socio);
    }

    // LEER TODOS LOS SOCIOS DEL TENANT ACTIVO
    public List<Socio> obtenerTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        return socioRepository.findByEmpresaId(tenantId);
    }

    // LEER UN SOCIO POR ID
    public Socio obtenerPorId(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        return socioRepository.findById(id)
                .filter(s -> s.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado en esta institucion."));
    }

    // ACTUALIZAR SOCIO
    @Transactional
    public Socio actualizarSocio(Integer id, Socio datosNuevos) {
        Socio socioExistente = obtenerPorId(id);

        // Mapeo selectivo de campos permitidos para actualización administrativa
        socioExistente.setNombresCompletos(datosNuevos.getNombresCompletos());
        socioExistente.setDireccion(datosNuevos.getDireccion());
        socioExistente.setTelefono(datosNuevos.getTelefono());
        socioExistente.setCorreo(datosNuevos.getCorreo());
        socioExistente.setActividadEconomica(datosNuevos.getActividadEconomica());
        socioExistente.setIngresosMensuales(datosNuevos.getIngresosMensuales());
        socioExistente.setGastosMensuales(datosNuevos.getGastosMensuales());
        socioExistente.setDeudasActuales(datosNuevos.getDeudasActuales());
        socioExistente.setEsPep(datosNuevos.getEsPep());
        socioExistente.setEstado(datosNuevos.getEstado());

        return socioRepository.save(socioExistente);
    }

    // ELIMINACIÓN LÓGICA (Inactivación por seguridad contable de historial)
    @Transactional
    public void eliminarLogico(Integer id) {
        Socio socio = obtenerPorId(id);
        socio.setEstado("INACTIVO");
        socioRepository.save(socio);
    }
}
