package com.cooperativa.core.repository;

import com.cooperativa.core.model.CuentasAhorros;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
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

    // Recupera cuentas por estado, tipo y empresa
    List<CuentasAhorros> findByEstadoAndTipoAndEmpresaId(String estado, String tipo, Integer empresaId);

    // CÓMPUTO RÁPIDO CONSOLIDADO DEL DEVENGO DIARIO DE INTERESES (PREVENCIÓN DE RIVALIDAD/SERIALIZABLE)
    @Query(value = "SELECT COALESCE(SUM(ROUND(saldo * tasa_interes_anual / 36000.0, 2)), 0.00) " +
                   "FROM cuentas_ahorros " +
                   "WHERE estado = 'ACTIVA' " +
                   "  AND tipo IN ('AHORRO_VISTA', 'PLAZO_FIJO', 'AHORRO_PROGRAMADO') " +
                   "  AND empresa_id = :empresaId " +
                   "  AND saldo > 0 " +
                   "  AND tasa_interes_anual > 0", nativeQuery = true)
    BigDecimal calcularTotalInteresDevengado(@Param("empresaId") Integer empresaId);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE cuentas_ahorros " +
                   "SET interes_acumulado = ROUND(interes_acumulado + ROUND(saldo * tasa_interes_anual / 36000.0, 2), 2) " +
                   "WHERE estado = 'ACTIVA' " +
                   "  AND tipo IN ('AHORRO_VISTA', 'PLAZO_FIJO', 'AHORRO_PROGRAMADO') " +
                   "  AND empresa_id = :empresaId " +
                   "  AND saldo > 0 " +
                   "  AND tasa_interes_anual > 0", nativeQuery = true)
    int ejecutarDevengoInteresesDiarios(@Param("empresaId") Integer empresaId);

    @Query(value = "SELECT COALESCE(p.cuenta_contable_gasto_id, e.cuenta_contable_gastos_intereses_id) AS gasto_id, " +
                   "       COALESCE(SUM(ROUND(c.saldo * c.tasa_interes_anual / 36000.0, 2)), 0.00) AS total " +
                   "FROM cuentas_ahorros c " +
                   "JOIN empresas e ON e.id = c.empresa_id " +
                   "LEFT JOIN productos_ahorro p ON p.id = c.producto_ahorro_id " +
                   "WHERE c.estado = 'ACTIVA' " +
                   "  AND c.tipo IN ('AHORRO_VISTA', 'PLAZO_FIJO', 'AHORRO_PROGRAMADO') " +
                   "  AND c.empresa_id = :empresaId " +
                   "  AND c.saldo > 0 " +
                   "  AND c.tasa_interes_anual > 0 " +
                   "GROUP BY gasto_id", nativeQuery = true)
    List<Object[]> obtenerInteresDevengadoAgrupadoPorGasto(@Param("empresaId") Integer empresaId);

    @Query("SELECT c FROM CuentasAhorros c " +
           "WHERE c.estado = 'ACTIVA' " +
           "  AND c.tipo IN ('PLAZO_FIJO', 'AHORRO_PROGRAMADO') " +
           "  AND c.fechaVencimiento <= :fecha " +
           "  AND c.empresaId = :empresaId")
    List<CuentasAhorros> findInversionesVencidas(
            @Param("fecha") java.time.LocalDate fecha,
            @Param("empresaId") Integer empresaId);
}