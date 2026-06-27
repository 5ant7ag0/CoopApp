package com.cooperativa.core.repository;

import com.cooperativa.core.model.ProductoAhorro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoAhorroRepository extends JpaRepository<ProductoAhorro, Integer> {
    List<ProductoAhorro> findByEmpresaId(Integer empresaId);
    List<ProductoAhorro> findByEstadoAndEmpresaId(String estado, Integer empresaId);
    Optional<ProductoAhorro> findByIdAndEmpresaId(Integer id, Integer empresaId);
}
