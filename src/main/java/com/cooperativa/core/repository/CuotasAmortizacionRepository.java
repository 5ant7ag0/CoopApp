package com.cooperativa.core.repository;

import com.cooperativa.core.model.CuotasAmortizacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface CuotasAmortizacionRepository extends JpaRepository<CuotasAmortizacion, Long> {

    // Recupera la tabla de pagos física de un crédito para pintarla en React Native
    List<CuotasAmortizacion> findByCreditoIdOrderByNumeroCuotaAsc(Integer creditoId);

    @Query("SELECT c FROM CuotasAmortizacion c " +
           "JOIN FETCH c.credito cr " +
           "JOIN FETCH cr.socio s " +
           "WHERE cr.estado IN ('DESEMBOLSADO', 'EN_MORA') " +
           "AND c.fechaVencimiento <= :fecha " +
           "AND c.estado <> 'PAGADA' " +
           "ORDER BY cr.id ASC, c.numeroCuota ASC")
    List<CuotasAmortizacion> findPendingCuotasExigibles(@Param("fecha") LocalDate fecha);
}