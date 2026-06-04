package com.cooperativa.core.repository;

import com.cooperativa.core.model.Empresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {

    // Buscar empresa por RUC para validacion de unicidad
    Optional<Empresa> findByRuc(String ruc);

    // Buscar empresa por codigo SEPS para validacion de unicidad
    Optional<Empresa> findByCodigoSeps(String codigoSeps);
}
