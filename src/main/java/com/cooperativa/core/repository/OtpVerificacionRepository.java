package com.cooperativa.core.repository;

import com.cooperativa.core.model.OtpVerificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpVerificacionRepository extends JpaRepository<OtpVerificacion, Integer> {
    Optional<OtpVerificacion> findFirstByEmailAndEmpresaIdAndVerificadoFalseAndFechaExpiracionAfterOrderByCreatedAtDesc(
        String email, Integer empresaId, LocalDateTime ahora
    );
    
    boolean existsByEmailAndEmpresaIdAndVerificadoTrue(String email, Integer empresaId);
}
