package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.LogsAuditoria;
import com.cooperativa.core.repository.LogsAuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LogsAuditoriaService {

    @Autowired
    private LogsAuditoriaRepository auditoriaRepository;

    /**
     * Recupera el historial completo de acciones críticas de la cooperativa activa,
     * ordenado cronológicamente desde el más reciente.
     */
    public List<LogsAuditoria> obtenerAuditoriaGlobal() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return auditoriaRepository.findByEmpresaIdOrderByFechaDesc(tenantId);
    }
}