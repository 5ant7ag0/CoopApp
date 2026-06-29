package com.cooperativa.core.repository;

import com.cooperativa.core.model.LogsAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;
@Repository
public interface LogsAuditoriaRepository extends JpaRepository<LogsAuditoria, Long> {

    // Recupera las trazas de auditoría de una sola cooperativa ordenadas cronológicamente
    List<LogsAuditoria> findByEmpresaIdOrderByFechaDesc(Integer empresaId);

    // Permite al auditor interno rastrear todos los cambios hechos sobre una fila específica de un módulo
    List<LogsAuditoria> findByTablaAfectadaAndRegistroIdAndEmpresaId(String tablaAfectada, Integer registroId, Integer empresaId);

    // Permite filtrar trazas de auditoría específicas por inquilino, tabla y acción
    List<LogsAuditoria> findByEmpresaIdAndTablaAfectadaAndAccionOrderByFechaDesc(Integer empresaId, String tablaAfectada, String accion);

    // Permite filtrar los logs de un usuario específico dentro de un tenant sin paginación (legacy/export)
    List<LogsAuditoria> findByUsuarioAdminIdAndEmpresaIdOrderByFechaDesc(Integer usuarioAdminId, Integer empresaId);

    // Paginación y filtro de fechas
    Page<LogsAuditoria> findByUsuarioAdminIdAndEmpresaIdAndFechaBetweenOrderByFechaDesc(Integer usuarioAdminId, Integer empresaId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    // Paginación sin filtro de fechas
    Page<LogsAuditoria> findByUsuarioAdminIdAndEmpresaIdOrderByFechaDesc(Integer usuarioAdminId, Integer empresaId, Pageable pageable);
}