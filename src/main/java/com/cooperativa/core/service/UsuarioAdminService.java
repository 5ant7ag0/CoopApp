package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import com.cooperativa.core.repository.LogsAuditoriaRepository;
import com.cooperativa.core.model.LogsAuditoria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class UsuarioAdminService {

    @Autowired
    private UsuarioAdminRepository usuarioRepository;

    @Autowired
    private LogsAuditoriaRepository logsRepository;

    @Autowired
    private com.cooperativa.core.security.EncryptionService encryptionService;

    @Autowired
    private com.cooperativa.core.repository.EmpresaRepository empresaRepository;

    private boolean validarCedulaEcuatoriana(String ced) {
        if (ced == null || ced.length() != 10) return false;
        try {
            int provincia = Integer.parseInt(ced.substring(0, 2));
            if (provincia < 1 || provincia > 24) return false;
            int tercerDigito = Integer.parseInt(ced.substring(2, 3));
            if (tercerDigito >= 6) return false;

            int[] coeficientes = {2, 1, 2, 1, 2, 1, 2, 1, 2};
            int suma = 0;
            for (int i = 0; i < 9; i++) {
                int valor = Character.getNumericValue(ced.charAt(i)) * coeficientes[i];
                if (valor >= 10) valor -= 9;
                suma += valor;
            }
            int verificadorCalculado = ((int) Math.ceil(suma / 10.0) * 10) - suma;
            int verificadorReal = Character.getNumericValue(ced.charAt(9));
            return verificadorCalculado == verificadorReal;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Transactional
    public UsuariosAdmin crearUsuario(UsuariosAdmin usuario) {
        Integer tenantId = TenantContext.getCurrentTenant();

        // Validar límite de usuarios
        com.cooperativa.core.model.Empresa empresa = empresaRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalStateException("Empresa no encontrada"));
            
        if (empresa.getLimiteUsuariosAdmin() != null) {
            long currentCount = usuarioRepository.countByEmpresaId(tenantId);
            if (currentCount >= empresa.getLimiteUsuariosAdmin()) {
                throw new com.cooperativa.core.exception.ResourceLimitExceededException(
                    "Error Comercial: Ha alcanzado el límite máximo de " + empresa.getLimiteUsuariosAdmin() + " usuarios administrativos para su plan actual."
                );
            }
        }

        // Validar cédula ecuatoriana
        if (!validarCedulaEcuatoriana(usuario.getIdentificacion())) {
            throw new IllegalArgumentException("Error: La cédula ingresada '" + usuario.getIdentificacion() + "' no es una cédula ecuatoriana válida.");
        }

        // Regla: Cédula única en la cooperativa
        if (usuarioRepository.findByUsernameAndEmpresaId(usuario.getUsername(), tenantId).isPresent()) {
            throw new IllegalStateException("Error: El nombre de usuario '" + usuario.getUsername() + "' ya esta en uso.");
        }

        // Encriptar la contrasena con BCrypt antes de guardar en la base de datos
        String plainPassword = usuario.getPasswordHash();
        usuario.setPasswordHash(encryptionService.hashPassword(plainPassword));
        
        usuario.setEmpresaId(tenantId);
        usuario.setCambiarPasswordProximoInicio(true); // Forzar cambio en primer inicio
        return usuarioRepository.save(usuario);
    }

    public List<UsuariosAdmin> obtenerTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        return usuarioRepository.findByEmpresaId(tenantId);
    }

    @Transactional
    public UsuariosAdmin actualizarUsuario(Integer id, UsuariosAdmin dto) {
        Integer tenantId = TenantContext.getCurrentTenant();
        UsuariosAdmin usuario = usuarioRepository.findById(id)
                .filter(u -> u.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        // Cédula es inmutable tras la creación
        if (dto.getIdentificacion() != null && !dto.getIdentificacion().equals(usuario.getIdentificacion())) {
            throw new IllegalStateException("Error: La identificación del empleado es inmutable tras la creación.");
        }

        usuario.setNombresCompletos(dto.getNombresCompletos());
        usuario.setCorreo(dto.getCorreo());
        usuario.setRol(dto.getRol());
        usuario.setEstado(dto.getEstado());
        usuario.setTelefono(dto.getTelefono());
        usuario.setDireccion(dto.getDireccion());
        usuario.setFotoPerfilUrl(dto.getFotoPerfilUrl());
        usuario.setCambiarPasswordProximoInicio(dto.isCambiarPasswordProximoInicio());
        usuario.setCajaId(dto.getCajaId());
        usuario.setLimiteTransaccionMax(dto.getLimiteTransaccionMax());

        // Si se ingresa una nueva contraseña, se encripta y se fuerza el cambio
        if (dto.getPasswordHash() != null && !dto.getPasswordHash().trim().isEmpty()) {
            usuario.setPasswordHash(encryptionService.hashPassword(dto.getPasswordHash().trim()));
            usuario.setCambiarPasswordProximoInicio(true);
        }

        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void cambiarClaveProximoInicio(String username, String passwordNueva) {
        Integer tenantId = TenantContext.getCurrentTenant();
        UsuariosAdmin usuario = usuarioRepository.findByUsernameAndEmpresaId(username, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

        if (passwordNueva == null || passwordNueva.trim().isEmpty()) {
            throw new IllegalArgumentException("La nueva contraseña no puede estar vacía.");
        }

        usuario.setPasswordHash(encryptionService.hashPassword(passwordNueva.trim()));
        usuario.setCambiarPasswordProximoInicio(false);
        usuarioRepository.save(usuario);
    }

    public Page<LogsAuditoria> obtenerLogsEmpleado(Integer usuarioAdminId, LocalDateTime fechaInicio, LocalDateTime fechaFin, Pageable pageable) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (fechaInicio != null && fechaFin != null) {
            return logsRepository.findByUsuarioAdminIdAndEmpresaIdAndFechaBetweenOrderByFechaDesc(usuarioAdminId, tenantId, fechaInicio, fechaFin, pageable);
        } else {
            return logsRepository.findByUsuarioAdminIdAndEmpresaIdOrderByFechaDesc(usuarioAdminId, tenantId, pageable);
        }
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