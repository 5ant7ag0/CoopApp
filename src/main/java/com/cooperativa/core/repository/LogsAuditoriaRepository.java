package com.cooperativa.core.repository;

import com.cooperativa.core.model.LogsAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LogsAuditoriaRepository extends JpaRepository<LogsAuditoria, Long> {

    // Recupera las trazas de auditoría de una sola cooperativa ordenadas cronológicamente
    List<LogsAuditoria> findByEmpresaIdOrderByFechaDesc(Integer empresaId);

    // Permite al auditor interno rastrear todos los cambios hechos sobre una fila específica de un módulo
    List<LogsAuditoria> findByTablaAfectadaAndRegistroIdAndEmpresaId(String tablaAfectada, Integer registroId, Integer empresaId);

    // Permite filtrar trazas de auditoría específicas por inquilino, tabla y acción
    List<LogsAuditoria> findByEmpresaIdAndTablaAfectadaAndAccionOrderByFechaDesc(Integer empresaId, String tablaAfectada, String accion);
}