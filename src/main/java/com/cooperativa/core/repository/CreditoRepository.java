package com.cooperativa.core.repository;

import com.cooperativa.core.model.Credito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditoRepository extends JpaRepository<Credito, Integer> {

    // Retorna todos los créditos otorgados por la cooperativa que realiza la consulta administrativa
    List<Credito> findByEmpresaId(Integer empresaId);

    // Recupera el historial crediticio de un socio específico dentro de su propia institución
    List<Credito> findBySocioIdAndEmpresaId(Integer socioId, Integer empresaId);

    // Busca un contrato específico validando el Tenant ID activo
    Optional<Credito> findByNumeroCreditoAndEmpresaId(String numeroCredito, Integer empresaId);

    // Obtiene el consolidado de riesgo crediticio para pintar indicadores (Socio ID -> [Total Creditos Activos, Cuotas Vencidas])
    @org.springframework.data.jpa.repository.Query("SELECT c.socio.id, COUNT(c.id), SUM(CASE WHEN cu.estado = 'EN_MORA' THEN 1 ELSE 0 END) " +
           "FROM Credito c " +
           "LEFT JOIN c.cuotas cu " +
           "WHERE c.socio.empresaId = :empresaId AND c.estado = 'DESEMBOLSADO' " +
           "GROUP BY c.socio.id")
    List<Object[]> findResumenRiesgoSocios(@org.springframework.data.repository.query.Param("empresaId") Integer empresaId);
}