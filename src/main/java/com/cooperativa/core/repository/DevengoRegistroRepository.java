package com.cooperativa.core.repository;

import com.cooperativa.core.model.DevengoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DevengoRegistroRepository extends JpaRepository<DevengoRegistro, Integer> {

    // Buscar si ya se realizó el devengo en una fecha y empresa específicas
    Optional<DevengoRegistro> findByFechaDevengoAndEmpresaId(LocalDate fechaDevengo, Integer empresaId);
}
