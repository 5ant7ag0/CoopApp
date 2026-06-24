package com.cooperativa.core.repository;

import com.cooperativa.core.model.AsientosDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AsientosDetalleRepository extends JpaRepository<AsientosDetalle, Long> {

    // Recupera los renglones (débitos/créditos) que pertenecen a un asiento específico
    List<AsientosDetalle> findByAsientoCabeceraId(Long asientoCabeceraId);

    // Recupera transacciones previas a una fecha (para cálculo de Saldo Inicial)
    @Query("SELECT d FROM AsientosDetalle d JOIN d.asientoCabecera c WHERE d.planCuentas.id = :cuentaId AND c.empresaId = :empresaId AND c.fechaAsiento < :desde")
    List<AsientosDetalle> findBeforeDate(
        @Param("cuentaId") Integer cuentaId,
        @Param("empresaId") Integer empresaId,
        @Param("desde") java.time.LocalDateTime desde
    );

    // Recupera transacciones dentro de un rango de fechas ordenados cronológicamente
    @Query("SELECT d FROM AsientosDetalle d JOIN d.asientoCabecera c WHERE d.planCuentas.id = :cuentaId AND c.empresaId = :empresaId AND c.fechaAsiento >= :desde AND c.fechaAsiento <= :hasta ORDER BY c.fechaAsiento ASC, d.id ASC")
    List<AsientosDetalle> findBetweenDates(
        @Param("cuentaId") Integer cuentaId,
        @Param("empresaId") Integer empresaId,
        @Param("desde") java.time.LocalDateTime desde,
        @Param("hasta") java.time.LocalDateTime hasta
    );

    // Recupera de forma paginada las transacciones ordenadas cronológicamente
    @Query("SELECT d FROM AsientosDetalle d JOIN d.asientoCabecera c WHERE d.planCuentas.id = :cuentaId AND c.empresaId = :empresaId AND c.fechaAsiento >= :desde AND c.fechaAsiento <= :hasta ORDER BY c.fechaAsiento ASC, d.id ASC")
    org.springframework.data.domain.Page<AsientosDetalle> findBetweenDatesPaged(
        @Param("cuentaId") Integer cuentaId,
        @Param("empresaId") Integer empresaId,
        @Param("desde") java.time.LocalDateTime desde,
        @Param("hasta") java.time.LocalDateTime hasta,
        org.springframework.data.domain.Pageable pageable
    );

    // Obtiene la suma de débitos y créditos del periodo completo
    @Query("SELECT " +
           "SUM(CASE WHEN d.tipoAsiento = 'DEBITO' THEN d.monto ELSE 0 END), " +
           "SUM(CASE WHEN d.tipoAsiento = 'CREDITO' THEN d.monto ELSE 0 END) " +
           "FROM AsientosDetalle d JOIN d.asientoCabecera c " +
           "WHERE d.planCuentas.id = :cuentaId " +
           "AND c.empresaId = :empresaId " +
           "AND c.fechaAsiento >= :desde " +
           "AND c.fechaAsiento <= :hasta")
    List<Object[]> sumMovementsPeriod(
        @Param("cuentaId") Integer cuentaId,
        @Param("empresaId") Integer empresaId,
        @Param("desde") java.time.LocalDateTime desde,
        @Param("hasta") java.time.LocalDateTime hasta
    );

    // Obtiene la suma de débitos y créditos acumulados previos a un registro/fecha de corte dentro del periodo
    @Query("SELECT " +
           "SUM(CASE WHEN d.tipoAsiento = 'DEBITO' THEN d.monto ELSE 0 END), " +
           "SUM(CASE WHEN d.tipoAsiento = 'CREDITO' THEN d.monto ELSE 0 END) " +
           "FROM AsientosDetalle d JOIN d.asientoCabecera c " +
           "WHERE d.planCuentas.id = :cuentaId " +
           "AND c.empresaId = :empresaId " +
           "AND c.fechaAsiento >= :desde " +
           "AND (c.fechaAsiento < :firstDate OR (c.fechaAsiento = :firstDate AND d.id < :firstId))")
    List<Object[]> sumMovementsBefore(
        @Param("cuentaId") Integer cuentaId,
        @Param("empresaId") Integer empresaId,
        @Param("desde") java.time.LocalDateTime desde,
        @Param("firstDate") java.time.LocalDateTime firstDate,
        @Param("firstId") Long firstId
    );

    // Verifica si existen detalles contables asociados a una cuenta
    boolean existsByPlanCuentasId(Integer planCuentasId);

    // Sumariza débitos y créditos agrupados por cuenta en un rango de fechas
    @Query("SELECT d.planCuentas.id, " +
           "SUM(CASE WHEN d.tipoAsiento = 'DEBITO' THEN d.monto ELSE 0 END), " +
           "SUM(CASE WHEN d.tipoAsiento = 'CREDITO' THEN d.monto ELSE 0 END) " +
           "FROM AsientosDetalle d JOIN d.asientoCabecera c " +
           "WHERE c.empresaId = :empresaId " +
           "AND c.fechaAsiento >= :desde " +
           "AND c.fechaAsiento <= :hasta " +
           "GROUP BY d.planCuentas.id")
    List<Object[]> sumGroupedByCuenta(
        @Param("empresaId") Integer empresaId,
        @Param("desde") java.time.LocalDateTime desde,
        @Param("hasta") java.time.LocalDateTime hasta
    );

    // Sumariza débitos y créditos agrupados por cuenta acumulados hasta una fecha de corte
    @Query("SELECT d.planCuentas.id, " +
           "SUM(CASE WHEN d.tipoAsiento = 'DEBITO' THEN d.monto ELSE 0 END), " +
           "SUM(CASE WHEN d.tipoAsiento = 'CREDITO' THEN d.monto ELSE 0 END) " +
           "FROM AsientosDetalle d JOIN d.asientoCabecera c " +
           "WHERE c.empresaId = :empresaId " +
           "AND c.fechaAsiento <= :corte " +
           "GROUP BY d.planCuentas.id")
    List<Object[]> sumGroupedByCuentaBefore(
        @Param("empresaId") Integer empresaId,
        @Param("corte") java.time.LocalDateTime corte
    );
}