package com.cooperativa.core.repository;

import com.cooperativa.core.model.PlanCuentas;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlanCuentasRepository extends JpaRepository<PlanCuentas, Integer> {

    // Encuentra todas las cuentas pertenecientes a la cooperativa activa
    List<PlanCuentas> findByEmpresaId(Integer empresaId);

    // Encuentra todas las cuentas ordenadas por código contable de forma ascendente
    List<PlanCuentas> findByEmpresaIdOrderByCodigoContableAsc(Integer empresaId);

    // Busca una cuenta específica por su código (Ej: "1.1.01.05") dentro de la cooperativa activa
    Optional<PlanCuentas> findByCodigoContableAndEmpresaId(String codigoContable, Integer empresaId);

    // Busca una cuenta específica por su ID dentro de la cooperativa activa
    Optional<PlanCuentas> findByIdAndEmpresaId(Integer id, Integer empresaId);
}