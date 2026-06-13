package com.cooperativa.core.repository;

import com.cooperativa.core.model.TransaccionesLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransaccionesLedgerRepository extends JpaRepository<TransaccionesLedger, Long> {

    // Recupera el historial transaccional completo del libro mayor de la cooperativa activa
    List<TransaccionesLedger> findByEmpresaId(Integer empresaId);

    // Recupera las transacciones de una cuenta de ahorros específica ordenadas por fecha contable de forma descendente
    List<TransaccionesLedger> findByCuentaIdOrderByFechaContableDesc(Integer cuentaId);

    // AGREGADO: Buscar transacciones operadas por un cajero a través de un canal en un rango de fechas contables
    List<TransaccionesLedger> findByUsuarioAdminIdAndCanalAndFechaContableBetween(
            Integer usuarioAdminId, String canal, java.time.LocalDateTime desde, java.time.LocalDateTime hasta);
}