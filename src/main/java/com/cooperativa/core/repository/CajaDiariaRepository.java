package com.cooperativa.core.repository;

import com.cooperativa.core.model.CajaDiaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaDiariaRepository extends JpaRepository<CajaDiaria, Integer> {

    // Buscar la caja activa (APERTURADA) para un cajero específico dentro del inquilino (Tenant)
    Optional<CajaDiaria> findByUsuarioCajeroIdAndEstadoAndEmpresaId(Integer usuarioCajeroId, String estado, Integer empresaId);

    // Buscar caja de un cajero para una fecha específica
    Optional<CajaDiaria> findByUsuarioCajeroIdAndFechaContableAndEmpresaId(Integer usuarioCajeroId, LocalDate fechaContable, Integer empresaId);

    // Listar cajas por rango de fechas y empresa
    List<CajaDiaria> findByFechaContableBetweenAndEmpresaId(LocalDate desde, LocalDate hasta, Integer empresaId);

    // Listar todas las cajas de una empresa
    List<CajaDiaria> findByEmpresaId(Integer empresaId);
}
