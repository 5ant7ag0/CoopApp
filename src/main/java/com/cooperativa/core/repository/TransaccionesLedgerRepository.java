package com.cooperativa.core.repository;

import com.cooperativa.core.model.TransaccionesLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TransaccionesLedgerRepository extends JpaRepository<TransaccionesLedger, Long> {

    // Recupera el historial transaccional completo del libro mayor de la cooperativa activa
    List<TransaccionesLedger> findByEmpresaId(Integer empresaId);
}