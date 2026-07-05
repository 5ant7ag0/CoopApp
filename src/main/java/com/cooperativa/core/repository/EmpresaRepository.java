package com.cooperativa.core.repository;

import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.model.TenantEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, Integer> {
    // Los métodos básicos de búsqueda por ID (Tenant ID) ya vienen incluidos por defecto en JpaRepository

    // Buscar empresa por RUC para validacion de unicidad
    Optional<Empresa> findByRuc(String ruc);

    // Buscar empresa por codigo SEPS para validacion de unicidad
    Optional<Empresa> findByCodigoSeps(String codigoSeps);

    java.util.List<Empresa> findByEstado(TenantEstado estado);

    @org.springframework.data.jpa.repository.Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM empresas WHERE ruc = :ruc", nativeQuery = true)
    boolean existsByRucRaw(@org.springframework.data.repository.query.Param("ruc") String ruc);

    @org.springframework.data.jpa.repository.Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM empresas WHERE codigo_seps = :codigoSeps", nativeQuery = true)
    boolean existsByCodigoSepsRaw(@org.springframework.data.repository.query.Param("codigoSeps") String codigoSeps);

    @org.springframework.data.jpa.repository.Query(value = "SELECT id, ruc, razon_social, nombre_comercial, representante_legal, correo_institucional, estado, limite_usuarios_admin, limite_socios, created_at FROM empresas", nativeQuery = true)
    java.util.List<Object[]> findAllTenantsRaw();
}