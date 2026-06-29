package com.cooperativa.core.repository;

import com.cooperativa.core.model.Agencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgenciaRepository extends JpaRepository<Agencia, Integer> {
    Optional<Agencia> findByCodigo(String codigo);
}
