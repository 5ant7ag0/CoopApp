package com.cooperativa.core.repository;

import com.cooperativa.core.model.CapitalizacionRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CapitalizacionRegistroRepository extends JpaRepository<CapitalizacionRegistro, Integer> {

    // Buscar si ya se realizó la capitalización en un año, mes y empresa específicos
    Optional<CapitalizacionRegistro> findByAnioAndMesAndEmpresaId(Integer anio, Integer mes, Integer empresaId);
}
