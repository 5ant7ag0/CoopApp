package com.cooperativa.core.repository;

import com.cooperativa.core.model.TokensRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TokensRecuperacionRepository extends JpaRepository<TokensRecuperacion, Integer> {

    // Buscar token por su hash y el Tenant validado
    Optional<TokensRecuperacion> findByTokenHashAndEmpresaId(String tokenHash, Integer empresaId);

    // Buscar el último token activo de recuperación para un socio con bloqueo de escritura pesimista
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TokensRecuperacion> findFirstBySocioIdAndUtilizadoFalseAndFechaExpiracionAfterOrderByCreatedAtDesc(Integer socioId, LocalDateTime ahora);
}
