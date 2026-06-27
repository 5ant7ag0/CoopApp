package com.cooperativa.core.service;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.dto.SocioRequestDTO;
import com.cooperativa.core.model.Socio;
import com.cooperativa.core.repository.SocioRepository;
import com.cooperativa.core.repository.OtpVerificacionRepository;
import com.cooperativa.core.repository.TokensRecuperacionRepository;
import com.cooperativa.core.model.TokensRecuperacion;
import com.cooperativa.core.model.LogsAuditoria;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.repository.UsuarioAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Map;

@Service
public class SocioService {

    @Autowired
    private SocioRepository socioRepository;

    @Autowired
    private OtpVerificacionRepository otpVerificacionRepository;

    @Autowired
    private ResendService resendService;

    @Autowired
    private TokensRecuperacionRepository tokensRecuperacionRepository;

    @Autowired
    private UsuarioAdminRepository usuarioAdminRepository;

    @Autowired
    private LogsAuditoriaService logsAuditoriaService;

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

        // Regla de Onboarding Estricto: El correo debe estar verificado con OTP
        if (!otpVerificacionRepository.existsByEmailAndEmpresaIdAndVerificadoTrue(dto.getCorreo().trim().toLowerCase(), tenantId)) {
            throw new IllegalStateException("Error de Onboarding: El correo electrónico " + dto.getCorreo() + " no ha sido verificado con código OTP.");
        }

        Socio socio = new Socio();
        socio.setTipoIdentificacion(dto.getTipoIdentificacion());
        socio.setIdentificacion(dto.getIdentificacion());
        socio.setNombresCompletos(dto.getNombresCompletos());
        socio.setDireccion(dto.getDireccion());
        socio.setTelefono(dto.getTelefono());
        socio.setCorreo(dto.getCorreo());
        socio.setActividadEconomica(dto.getActividadEconomica());
        socio.setLugarTrabajo(dto.getLugarTrabajo());
        socio.setIngresosMensuales(dto.getIngresosMensuales());
        socio.setGastosMensuales(dto.getGastosMensuales());
        socio.setDeudasActuales(dto.getDeudasActuales());
        socio.setFotoPerfilUrl(dto.getFotoPerfilUrl());
        socio.setFotoCedulaFrontalUrl(dto.getFotoCedulaFrontalUrl());
        socio.setFotoCedulaPosteriorUrl(dto.getFotoCedulaPosteriorUrl());
        socio.setEstadoCivil(dto.getEstadoCivil());
        socio.setProfesion(dto.getProfesion());
        if (dto.getEsPep() != null) {
            socio.setEsPep(dto.getEsPep());
        }
        if (dto.getEstado() != null) {
            socio.setEstado(dto.getEstado());
        }

        Socio socioCreado = socioRepository.save(socio);

        // Generar Token de Recuperacion/Establecimiento de contraseña
        String tokenRaw = UUID.randomUUID().toString();
        String tokenHash = hashSha256(tokenRaw);

        TokensRecuperacion tokenRec = new TokensRecuperacion();
        tokenRec.setSocio(socioCreado);
        tokenRec.setTokenHash(tokenHash);
        tokenRec.setCanal("CORREO");
        tokenRec.setFechaExpiracion(LocalDateTime.now().plusMinutes(15));
        tokenRec.setUtilizado(false);
        tokenRec.setIntentosFallidos(0);
        tokensRecuperacionRepository.save(tokenRec);

        // Cuerpo HTML Corporativo de Bienvenida y Activacion de Banca Digital
        String welcomeLink = "http://localhost:5173/establecer-password?token=" + tokenRaw + "&identificacion=" + socioCreado.getIdentificacion();
        String welcomeHtml = String.format(
            "<!DOCTYPE html><html><head><style>" +
            "body { font-family: Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 20px; }" +
            ".card { max-width: 500px; margin: 0 auto; background: white; border-radius: 16px; padding: 30px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }" +
            ".header { text-align: center; border-bottom: 2px solid #0054A6; padding-bottom: 15px; margin-bottom: 25px; }" +
            ".logo { font-size: 20px; font-weight: bold; color: #0054A6; margin: 0; }" +
            ".title { font-size: 18px; font-weight: bold; margin-bottom: 15px; color: #0f172a; }" +
            ".btn { display: inline-block; background-color: #0054A6; color: white !important; font-weight: bold; padding: 12px 24px; border-radius: 8px; text-decoration: none; margin-top: 20px; text-align: center; }" +
            ".footer { text-align: center; font-size: 11px; color: #94a3b8; margin-top: 30px; border-top: 1px solid #f1f5f9; padding-top: 15px; }" +
            "</style></head><body><div class=\"card\">" +
            "<div class=\"header\"><div class=\"logo\">COOPERATIVA DE AHORRO Y CRÉDITO ITQ</div></div>" +
            "<div class=\"title\">¡Bienvenido a la Cooperativa ITQ!</div>" +
            "<p>Estimado/a <strong>%s</strong>,</p>" +
            "<p>Su registro como socio ha sido completado con éxito. Ahora puede activar su acceso a la Banca Digital para realizar consultas, transferencias y pagos en línea.</p>" +
            "<p>Por favor, haga clic en el botón a continuación para configurar su contraseña y activar su cuenta:</p>" +
            "<div style=\"text-align: center;\"><a href=\"%s\" class=\"btn\">Activar Banca Digital</a></div>" +
            "<p style=\"font-size: 12px; color: #64748b; margin-top: 20px;\">Este enlace es de uso único y tiene una validez de 15 minutos.</p>" +
            "<div class=\"footer\">Este es un mensaje automático de la Cooperativa ITQ Ltda. Por favor no responda a este correo.</div>" +
            "</div></body></html>",
            socioCreado.getNombresCompletos(),
            welcomeLink
        );

        resendService.enviarCorreo(socioCreado.getCorreo(), "Activa tu Banca Digital - Cooperativa", welcomeHtml);

        return socioCreado;
    }

    private String hashSha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Error al generar hash de seguridad para la sesion: ", ex);
        }
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
        socioExistente.setLugarTrabajo(dto.getLugarTrabajo());
        socioExistente.setIngresosMensuales(dto.getIngresosMensuales());
        socioExistente.setGastosMensuales(dto.getGastosMensuales());
        socioExistente.setDeudasActuales(dto.getDeudasActuales());
        socioExistente.setFotoPerfilUrl(dto.getFotoPerfilUrl());
        socioExistente.setFotoCedulaFrontalUrl(dto.getFotoCedulaFrontalUrl());
        socioExistente.setFotoCedulaPosteriorUrl(dto.getFotoCedulaPosteriorUrl());
        socioExistente.setFirmaUrl(dto.getFirmaUrl());
        socioExistente.setEstadoCivil(dto.getEstadoCivil());
        socioExistente.setProfesion(dto.getProfesion());
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

    // GUARDAR CÉDULA FRONTAL FÍSICA
    @Transactional(rollbackFor = Exception.class)
    public String guardarCedulaFrontal(Integer id, org.springframework.web.multipart.MultipartFile file, String username, String ip, String userAgent) throws Exception {
        Socio socio = obtenerPorId(id);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Error: El archivo provisto esta vacio.");
        }

        // Crear el directorio uploads/kyc si no existe
        String uploadDir = System.getProperty("user.dir") + "/uploads/kyc/";
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String filename = socio.getIdentificacion() + "_frontal_" + System.currentTimeMillis() + "." + extension;
        java.io.File destFile = new java.io.File(dir, filename);

        // Limpiar fotos previas de la cedula frontal del mismo socio
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.getName().startsWith(socio.getIdentificacion() + "_frontal_")) {
                    f.delete();
                }
            }
        }

        file.transferTo(destFile);

        String url = "/uploads/kyc/" + filename;
        String valorAnterior = socio.getFotoCedulaFrontalUrl();
        socio.setFotoCedulaFrontalUrl(url);
        socioRepository.save(socio);

        // Registrar Auditoria
        registrarAuditoria(socio.getEmpresaId(), socio.getId(), "ACTUALIZAR_CEDULA_FRONTAL", username, ip, userAgent,
            Map.of("campo", "fotoCedulaFrontalUrl", "valorAnterior", (valorAnterior != null ? valorAnterior : "NINGUNO")),
            Map.of("campo", "fotoCedulaFrontalUrl", "valorNuevo", url));

        return url;
    }

    // GUARDAR CÉDULA POSTERIOR FÍSICA
    @Transactional(rollbackFor = Exception.class)
    public String guardarCedulaPosterior(Integer id, org.springframework.web.multipart.MultipartFile file, String username, String ip, String userAgent) throws Exception {
        Socio socio = obtenerPorId(id);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Error: El archivo provisto esta vacio.");
        }

        // Crear el directorio uploads/kyc si no existe
        String uploadDir = System.getProperty("user.dir") + "/uploads/kyc/";
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String filename = socio.getIdentificacion() + "_posterior_" + System.currentTimeMillis() + "." + extension;
        java.io.File destFile = new java.io.File(dir, filename);

        // Limpiar fotos previas de la cedula posterior del mismo socio
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.getName().startsWith(socio.getIdentificacion() + "_posterior_")) {
                    f.delete();
                }
            }
        }

        file.transferTo(destFile);

        String url = "/uploads/kyc/" + filename;
        String valorAnterior = socio.getFotoCedulaPosteriorUrl();
        socio.setFotoCedulaPosteriorUrl(url);
        socioRepository.save(socio);

        // Registrar Auditoria
        registrarAuditoria(socio.getEmpresaId(), socio.getId(), "ACTUALIZAR_CEDULA_POSTERIOR", username, ip, userAgent,
            Map.of("campo", "fotoCedulaPosteriorUrl", "valorAnterior", (valorAnterior != null ? valorAnterior : "NINGUNO")),
            Map.of("campo", "fotoCedulaPosteriorUrl", "valorNuevo", url));

        return url;
    }

    // GUARDAR FIRMA FÍSICA
    @Transactional(rollbackFor = Exception.class)
    public String guardarFirma(Integer id, org.springframework.web.multipart.MultipartFile file, String username, String ip, String userAgent) throws Exception {
        Socio socio = obtenerPorId(id);

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Error: El archivo provisto esta vacio.");
        }

        // Crear el directorio uploads/kyc si no existe
        String uploadDir = System.getProperty("user.dir") + "/uploads/kyc/";
        java.io.File dir = new java.io.File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "jpg";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }

        String filename = socio.getIdentificacion() + "_firma_" + System.currentTimeMillis() + "." + extension;
        java.io.File destFile = new java.io.File(dir, filename);

        // Limpiar fotos previas de la firma del mismo socio
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.getName().startsWith(socio.getIdentificacion() + "_firma_")) {
                    f.delete();
                }
            }
        }

        file.transferTo(destFile);

        String url = "/uploads/kyc/" + filename;
        String valorAnterior = socio.getFirmaUrl();
        socio.setFirmaUrl(url);
        socioRepository.save(socio);

        // Registrar Auditoria
        registrarAuditoria(socio.getEmpresaId(), socio.getId(), "ACTUALIZAR_FIRMA", username, ip, userAgent,
            Map.of("campo", "firmaUrl", "valorAnterior", (valorAnterior != null ? valorAnterior : "NINGUNO")),
            Map.of("campo", "firmaUrl", "valorNuevo", url));

        return url;
    }

    private void registrarAuditoria(Integer tenantId, Integer socioId, String accion, String username, String ip, String userAgent, Map<String, Object> anterior, Map<String, Object> nuevo) {
        Integer adminId = null;
        if (username != null) {
            adminId = usuarioAdminRepository.findByUsernameAndEmpresaId(username, tenantId)
                    .map(UsuariosAdmin::getId)
                    .orElse(null);
        }
        if (adminId == null) {
            List<UsuariosAdmin> admins = usuarioAdminRepository.findByEmpresaId(tenantId);
            if (!admins.isEmpty()) {
                adminId = admins.get(0).getId();
            }
        }

        LogsAuditoria log = new LogsAuditoria();
        log.setUsuarioAdminId(adminId);
        log.setAccion(accion);
        log.setTablaAfectada("socios");
        log.setRegistroId(socioId);
        log.setDireccionIp(ip != null ? ip : "127.0.0.1");
        log.setDispositivoInfo(userAgent != null ? userAgent : "Web Portal");
        log.setValorAnterior(anterior);
        log.setValorNuevo(nuevo);
        logsAuditoriaService.registrarLog(log);
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
