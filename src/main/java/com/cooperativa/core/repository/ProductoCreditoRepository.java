package com.cooperativa.core.repository;

import com.cooperativa.core.model.ProductoCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoCreditoRepository extends JpaRepository<ProductoCredito, Integer> {
    List<ProductoCredito> findByEmpresaId(Integer empresaId);
    List<ProductoCredito> findByEmpresaIdAndEstado(Integer empresaId, String estado);
    Optional<ProductoCredito> findByIdAndEmpresaId(Integer id, Integer empresaId);
}
