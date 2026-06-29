package com.cooperativa.core.repository;

import com.cooperativa.core.model.CajasVentanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CajasVentanillaRepository extends JpaRepository<CajasVentanilla, Integer> {
    List<CajasVentanilla> findByEmpresaId(Integer empresaId);
    List<CajasVentanilla> findByEmpresaIdAndEstado(Integer empresaId, String estado);
    java.util.Optional<CajasVentanilla> findByCodigo(String codigo);
    List<CajasVentanilla> findByCuentaContableId(Integer cuentaContableId);
}
