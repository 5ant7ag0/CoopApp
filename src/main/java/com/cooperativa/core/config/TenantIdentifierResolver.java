package com.cooperativa.core.config;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Integer> {

    @Override
    public Integer resolveCurrentTenantIdentifier() {
        Integer tenantId = TenantContext.getCurrentTenant();
        // Si no hay tenant en el hilo actual, retornamos un valor que no coincida con ningún tenant válido (ej. 0)
        // para evitar cargar datos de inquilinos reales accidentalmente.
        return tenantId != null ? tenantId : 0;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
