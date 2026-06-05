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
}