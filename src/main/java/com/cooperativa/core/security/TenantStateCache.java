package com.cooperativa.core.security;

import com.cooperativa.core.model.TenantEstado;
import com.cooperativa.core.repository.EmpresaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.DependsOn;
import jakarta.annotation.PostConstruct;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caché en memoria thread-safe para mantener los estados de los inquilinos (Tenants).
 * Permite invalidar en caliente operaciones de inquilinos suspendidos (Kill Switch)
 * sin afectar el rendimiento de validación del token JWT (latencia O(1)).
 */
@Component
@DependsOn("flyway")
public class TenantStateCache {

    // Conjunto de IDs de empresas (Tenants) que han sido suspendidos.
    private final Set<Integer> suspendedTenants = ConcurrentHashMap.newKeySet();

    @Autowired
    private EmpresaRepository empresaRepository;

    @PostConstruct
    public void init() {
        // Cargar en memoria todas las empresas suspendidas al iniciar
        empresaRepository.findByEstado(TenantEstado.SUSPENDIDO).forEach(e -> {
            suspendedTenants.add(e.getId());
        });
    }

    /**
     * Verifica si un Tenant está suspendido.
     */
    public boolean isSuspended(Integer tenantId) {
        if (tenantId == null) return false;
        return suspendedTenants.contains(tenantId);
    }

    /**
     * Agrega un Tenant a la lista de suspendidos (Kill Switch).
     */
    public void suspendTenant(Integer tenantId) {
        if (tenantId != null) {
            suspendedTenants.add(tenantId);
        }
    }

    /**
     * Remueve la suspensión de un Tenant (Reactivación).
     */
    public void reactivateTenant(Integer tenantId) {
        if (tenantId != null) {
            suspendedTenants.remove(tenantId);
        }
    }
}
