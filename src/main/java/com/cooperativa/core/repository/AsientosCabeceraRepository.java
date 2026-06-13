package com.cooperativa.core.repository;

import com.cooperativa.core.model.AsientosCabecera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AsientosCabeceraRepository extends JpaRepository<AsientosCabecera, Long> {

    // Recupera todas las cabeceras de asientos de la cooperativa activa
    List<AsientosCabecera> findByEmpresaId(Integer empresaId);

    // Recupera las cabeceras de asientos de la cooperativa activa creadas en un rango de fechas
    List<AsientosCabecera> findByEmpresaIdAndFechaAsientoBetweenOrderByFechaAsientoDesc(
            Integer empresaId,
            java.time.LocalDateTime inicio,
            java.time.LocalDateTime fin
    );
}