package com.cooperativa.core.repository;

import com.cooperativa.core.model.AsientosDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AsientosDetalleRepository extends JpaRepository<AsientosDetalle, Long> {

    // Recupera los renglones (débitos/créditos) que pertenecen a un asiento específico
    List<AsientosDetalle> findByAsientoCabeceraId(Long asientoCabeceraId);
}