package com.cooperativa.core.repository;

import com.cooperativa.core.model.CuentasAhorros;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface CuentasAhorrosRepository extends JpaRepository<CuentasAhorros, Integer> {

    // AGREGADO: Recupera todas las cuentas registradas bajo la misma cooperativa (Multi-Tenant)
    List<CuentasAhorros> findByEmpresaId(Integer empresaId);

    // Recupera las cuentas de ahorro o aportación de un socio específico dentro de la cooperativa activa
    List<CuentasAhorros> findBySocioIdAndEmpresaId(Integer socioId, Integer empresaId);

    // Encuentra una cuenta bancaria específica mediante su número único y el Tenant validado
    Optional<CuentasAhorros> findByNumeroCuentaAndEmpresaId(String numeroCuenta, Integer empresaId);
}