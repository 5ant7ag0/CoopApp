package com.cooperativa.core.repository;

import com.cooperativa.core.model.UsuariosAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioAdminRepository extends JpaRepository<UsuariosAdmin, Integer> {

    // Recupera todos los empleados de la cooperativa activa
    List<UsuariosAdmin> findByEmpresaId(Integer empresaId);

    // Sirve para el futuro Login: Buscar usuario por su username dentro del tenant
    Optional<UsuariosAdmin> findByUsernameAndEmpresaId(String username, Integer empresaId);
    
    // Obtener qué usuario está asignado a una caja específica
    Optional<UsuariosAdmin> findByCajaId(Integer cajaId);
}