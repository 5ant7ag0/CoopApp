package com.cooperativa.core.model;

import com.cooperativa.core.config.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    // Tablas para identificar la organización
    @Column(name = "empresa_id", nullable = false, updatable = false)
    private Integer empresaId;

    /**
     * Gancho automático antes de insertar un registro en PostgreSQL (JPA Lifecycle)
     */
    @PrePersist
    public void prePersist() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede guardar datos sin un X-Tenant-ID definido.");
        }
        this.empresaId = tenantId;
    }

    /**
     * Gancho automático antes de actualizar un registro en PostgreSQL
     */
    @PreUpdate
    public void preUpdate() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede actualizar datos sin un X-Tenant-ID definido.");
        }
        this.empresaId = tenantId;
    }
}