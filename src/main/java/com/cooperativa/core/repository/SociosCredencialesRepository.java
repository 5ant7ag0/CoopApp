package com.cooperativa.core.repository;

import com.cooperativa.core.model.SociosCredenciales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para la entidad SociosCredenciales.
 */
@Repository
public interface SociosCredencialesRepository extends JpaRepository<SociosCredenciales, Integer> {

    /**
     * Busca las credenciales por el ID del socio.
     */
    Optional<SociosCredenciales> findBySocioId(Integer socioId);

    /**
     * Busca las credenciales a partir de la identificacion del socio y el id de la cooperativa.
     */
    @Query("SELECT sc FROM SociosCredenciales sc WHERE sc.socio.identificacion = :identificacion AND sc.socio.empresaId = :empresaId")
    Optional<SociosCredenciales> findByIdentificacionAndEmpresaId(
            @Param("identificacion") String identificacion,
            @Param("empresaId") Integer empresaId);
}
