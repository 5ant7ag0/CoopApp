package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.LogsAuditoria;
import com.cooperativa.core.repository.LogsAuditoriaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Guarda una bitácora de auditoría en una transacción física independiente (PROPAGATION_REQUIRES_NEW).
     * Esto asegura que los logs se conserven en la base de datos incluso si la transacción de negocio principal realiza rollback.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LogsAuditoria registrarLog(LogsAuditoria log) {
        return auditoriaRepository.save(log);
    }
}