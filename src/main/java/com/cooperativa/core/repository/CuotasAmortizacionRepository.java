package com.cooperativa.core.repository;

import com.cooperativa.core.model.CuotasAmortizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CuotasAmortizacionRepository extends JpaRepository<CuotasAmortizacion, Long> {

    // Recupera la tabla de pagos física de un crédito para pintarla en React Native
    List<CuotasAmortizacion> findByCreditoIdOrderByNumeroCuotaAsc(Integer creditoId);
}