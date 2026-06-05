package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class UsuarioAdminService {

    @Autowired
    private UsuarioAdminRepository usuarioRepository;

    @Autowired
    private com.cooperativa.core.security.EncryptionService encryptionService;

    @Transactional
    public UsuariosAdmin crearUsuario(UsuariosAdmin usuario) {
        Integer tenantId = TenantContext.getCurrentTenant();

        // Regla: No pueden haber dos usernames iguales en la misma cooperativa
        if (usuarioRepository.findByUsernameAndEmpresaId(usuario.getUsername(), tenantId).isPresent()) {
            throw new IllegalStateException("Error: El nombre de usuario '" + usuario.getUsername() + "' ya esta en uso.");
        }

        // Encriptar la contrasena con BCrypt antes de guardar en la base de datos
        String plainPassword = usuario.getPasswordHash();
        usuario.setPasswordHash(encryptionService.hashPassword(plainPassword));
        
        usuario.setEmpresaId(tenantId);
        return usuarioRepository.save(usuario);
    }

    public List<UsuariosAdmin> obtenerTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return usuarioRepository.findByEmpresaId(tenantId);
    }

    @Transactional
    public void inactivarUsuario(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        UsuariosAdmin usuario = usuarioRepository.findById(id)
                .filter(u -> u.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        usuario.setEstado("INACTIVO");
        usuarioRepository.save(usuario);
    }
}