package com.cooperativa.core.repository;

import com.cooperativa.core.model.ControlSesiones;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la entidad ControlSesiones.
 */
@Repository
public interface ControlSesionesRepository extends JpaRepository<ControlSesiones, Integer> {

    /**
     * Busca una sesion activa por el hash de su token JWT.
     */
    Optional<ControlSesiones> findByTokenJwtHash(String tokenJwtHash);

    /**
     * Busca las sesiones activas de un usuario administrador.
     */
    List<ControlSesiones> findByUsuarioAdminIdAndEstado(Integer usuarioAdminId, String estado);

    /**
     * Busca las sesiones activas de un socio.
     */
    List<ControlSesiones> findBySocioIdAndEstado(Integer socioId, String estado);
}
