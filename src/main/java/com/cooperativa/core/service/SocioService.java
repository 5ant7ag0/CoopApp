package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.SocioRequestDTO;
import com.cooperativa.core.model.Socio;
import com.cooperativa.core.repository.SocioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class SocioService {

    @Autowired
    private SocioRepository socioRepository;

    // CREAR UN NUEVO SOCIO
    @Transactional(rollbackFor = Exception.class)
    public Socio crearSocio(SocioRequestDTO dto) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error de Seguridad: No se puede guardar datos sin un X-Tenant-ID definido.");
        }

        // Regla: Evitar duplicados de cédula/RUC en la misma cooperativa
        if (socioRepository.existsByIdentificacionAndEmpresaId(dto.getIdentificacion(), tenantId)) {
            throw new IllegalStateException("Error: Ya existe un socio registrado con la identificacion " + dto.getIdentificacion() + " en esta cooperativa.");
        }

        Socio socio = new Socio();
        socio.setTipoIdentificacion(dto.getTipoIdentificacion());
        socio.setIdentificacion(dto.getIdentificacion());
        socio.setNombresCompletos(dto.getNombresCompletos());
        socio.setDireccion(dto.getDireccion());
        socio.setTelefono(dto.getTelefono());
        socio.setCorreo(dto.getCorreo());
        socio.setActividadEconomica(dto.getActividadEconomica());
        socio.setIngresosMensuales(dto.getIngresosMensuales());
        socio.setGastosMensuales(dto.getGastosMensuales());
        socio.setDeudasActuales(dto.getDeudasActuales());
        socio.setFotoPerfilUrl(dto.getFotoPerfilUrl());
        socio.setFotoCedulaFrontalUrl(dto.getFotoCedulaFrontalUrl());
        socio.setFotoCedulaPosteriorUrl(dto.getFotoCedulaPosteriorUrl());
        if (dto.getEsPep() != null) {
            socio.setEsPep(dto.getEsPep());
        }
        if (dto.getEstado() != null) {
            socio.setEstado(dto.getEstado());
        }

        return socioRepository.save(socio);
    }

    // LEER TODOS LOS SOCIOS DEL TENANT ACTIVO
    public List<Socio> obtenerTodos() {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        return socioRepository.findByEmpresaId(tenantId);
    }

    // LEER UN SOCIO POR ID
    public Socio obtenerPorId(Integer id) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede obtener datos sin un X-Tenant-ID definido.");
        }
        return socioRepository.findById(id)
                .filter(s -> s.getEmpresaId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado en esta institucion."));
    }

    // ACTUALIZAR SOCIO
    @Transactional(rollbackFor = Exception.class)
    public Socio actualizarSocio(Integer id, SocioRequestDTO dto) {
        Socio socioExistente = obtenerPorId(id);

        // Mapeo selectivo de campos permitidos para actualización administrativa
        socioExistente.setNombresCompletos(dto.getNombresCompletos());
        socioExistente.setDireccion(dto.getDireccion());
        socioExistente.setTelefono(dto.getTelefono());
        socioExistente.setCorreo(dto.getCorreo());
        socioExistente.setActividadEconomica(dto.getActividadEconomica());
        socioExistente.setIngresosMensuales(dto.getIngresosMensuales());
        socioExistente.setGastosMensuales(dto.getGastosMensuales());
        socioExistente.setDeudasActuales(dto.getDeudasActuales());
        socioExistente.setFotoPerfilUrl(dto.getFotoPerfilUrl());
        if (dto.getEsPep() != null) {
            socioExistente.setEsPep(dto.getEsPep());
        }
        if (dto.getEstado() != null) {
            socioExistente.setEstado(dto.getEstado());
        }

        return socioRepository.save(socioExistente);
    }

    // GUARDAR FOTO DE PERFIL FISICA
    @Transactional(rollbackFor = Exception.class)
    public String guardarAvatar(Integer id, MultipartFile file) throws Exception {
        Socio socio = obtenerPorId(id);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Error: El archivo provisto esta vacio.");
        }

        // Crear el directorio uploads/perfil si no existe
        String uploadDir = System.getProperty("user.dir") + "/uploads/perfil/";
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Obtener extension
        String originalFilename = file.getOriginalFilename();
        String extension = "jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        // Generar nombre unico para romper cache
        String filename = "socio_" + id + "_" + System.currentTimeMillis() + "." + extension;
        java.io.File destFile = new java.io.File(dir, filename);

        // Limpiar fotos previas del mismo socio
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.getName().startsWith("socio_" + id + "_")) {
                    f.delete();
                }
            }
        }

        // Guardar archivo fisico
        file.transferTo(destFile);

        // Guardar ruta corta en la base de datos
        String avatarUrl = "/uploads/perfil/" + filename;
        socio.setFotoPerfilUrl(avatarUrl);
        socioRepository.save(socio);

        return avatarUrl;
    }

    // ELIMINAR FOTO DE PERFIL FISICA Y EN BASE DE DATOS
    @Transactional(rollbackFor = Exception.class)
    public void eliminarAvatar(Integer id) {
        Socio socio = obtenerPorId(id);

        // Borrar archivo fisico
        String uploadDir = System.getProperty("user.dir") + "/uploads/perfil/";
        java.io.File dir = new java.io.File(uploadDir);
        if (dir.exists()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.getName().startsWith("socio_" + id + "_")) {
                        f.delete();
                    }
                }
            }
        }

        socio.setFotoPerfilUrl(null);
        socioRepository.save(socio);
    }

    // ELIMINACIÓN LÓGICA (Inactivación por seguridad contable de historial)
    @Transactional(rollbackFor = Exception.class)
    public void eliminarLogico(Integer id) {
        Socio socio = obtenerPorId(id);
        socio.setEstado("INACTIVO");
        socioRepository.save(socio);
    }

    // BUSCAR SOCIO POR IDENTIFICACION
    public Socio buscarPorIdentificacion(String identificacion) {
        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("Error: No se puede buscar datos sin un X-Tenant-ID definido.");
        }
        return socioRepository.findByIdentificacionAndEmpresaId(identificacion, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Error: Socio no encontrado con la identificacion provista."));
    }
}
