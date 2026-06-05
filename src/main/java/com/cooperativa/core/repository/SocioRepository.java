package com.cooperativa.core.repository;

import com.cooperativa.core.model.Socio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface SocioRepository extends JpaRepository<Socio, Integer> {

    // Retorna únicamente los socios que pertenecen a la cooperativa que realiza la petición web/móvil
    List<Socio> findByEmpresaId(Integer empresaId);

    // Busca un socio por su número de cédula/RUC y valida que pertenezca al Tenant activo
    Optional<Socio> findByIdentificacionAndEmpresaId(String identificacion, Integer empresaId);

    // Verifica si ya existe un socio registrado con esa identificación en la misma cooperativa
    boolean existsByIdentificacionAndEmpresaId(String identificacion, Integer empresaId);
}