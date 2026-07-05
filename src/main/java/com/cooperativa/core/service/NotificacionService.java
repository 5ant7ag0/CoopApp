package com.cooperativa.core.service;

import com.cooperativa.core.model.Socio;
import com.cooperativa.core.model.UsuariosAdmin;
import com.cooperativa.core.util.EmailTemplateBuilder;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.model.Empresa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    @Value("${resend.api-key}")
    private String resendApiKey;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Autowired
    private EmailTemplateBuilder templateBuilder;

    @Autowired
    private EmpresaRepository empresaRepository;

    private String obtenerNombreCoop(Integer empresaId) {
        if (empresaId == null) {
            return "COOPERATIVA DE AHORRO Y CRÉDITO";
        }
        return empresaRepository.findById(empresaId)
                .map(Empresa::getNombreComercial)
                .orElse("COOPERATIVA DE AHORRO Y CRÉDITO");
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Envía correo electrónico de recuperación de clave vía Resend.
     * Genera un bloque OTP o un botón dependiendo de si el token es un código de 6 dígitos.
     */
    public void enviarRecuperacionCorreo(Socio socio, String token) {
        boolean isOtp = token != null && token.length() == 6 && token.matches("\\d+");
        String titulo = isOtp ? "Código de Verificación OTP" : "Restablecimiento de Contraseña";
        String mensaje = "Estimado/a " + socio.getNombresCompletos() + ", hemos recibido una solicitud para cambiar su contraseña digital.";
        
        String cuerpo;
        if (isOtp) {
            cuerpo = templateBuilder.buildOtpBlock(token);
        } else {
            cuerpo = templateBuilder.buildLinkButton("http://localhost:5173/recuperar-clave-socio?token=" + token + "&identificacion=" + socio.getIdentificacion(), "Restablecer Contraseña");
        }
        
        String notaPie = "Este " + (isOtp ? "código" : "enlace") + " es de uso único y expirará en 15 minutos.";
        String coopName = obtenerNombreCoop(socio.getEmpresaId());
        String htmlBody = templateBuilder.buildCorporateEmail(titulo, mensaje, cuerpo, notaPie, coopName);

        enviarCorreoResend(socio.getCorreo(), "Restablecimiento de Contraseña Digital - " + coopName, htmlBody);
    }

    private void enviarCorreoResend(String to, String subject, String htmlContent) {
        if (resendApiKey == null || resendApiKey.trim().isEmpty() || resendApiKey.startsWith("$")) {
            log.warn("=========================================================================");
            log.warn("[NotificacionService] SIMULACIÓN (RESEND_API_KEY no configurada)");
            log.warn("Para: {}", to);
            log.warn("Asunto: {}", subject);
            log.info("CONTENIDO HTML:\n{}", htmlContent);
            log.warn("=========================================================================");
            return;
        }

        try {
            String escapedHtml = htmlContent.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
            String jsonPayload = String.format(
                "{\"from\":\"%s\",\"to\":[\"%s\"],\"subject\":\"%s\",\"html\":\"%s\"}",
                fromEmail, to, subject, escapedHtml
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Correo enviado exitosamente a {} vía Resend", to);
            } else {
                log.error("Error al enviar correo vía Resend. Código: {}, Respuesta: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Excepción al intentar enviar correo vía Resend a {}", to, e);
        }
    }

    public void enviarRecuperacionCorreoAdmin(UsuariosAdmin admin, String link) {
        boolean isSaaS = admin.getEmpresaId() == null || admin.getEmpresaId() == 1;
        String titulo = "Restablecimiento de Contraseña (Administrador)";
        String mensaje = "Estimado/a " + admin.getNombresCompletos() + ", hemos recibido una solicitud para restablecer su contraseña de administrador.";
        
        String cuerpo = templateBuilder.buildLinkButton(link, "Crear Nueva Contraseña");
        String notaPie = "Este enlace es de uso único y expirará en 15 minutos. Si usted no solicitó esto, por favor ignore este correo.";
        
        String htmlBody;
        String subject;
        if (isSaaS) {
            htmlBody = templateBuilder.buildSaaSEmail(titulo, mensaje, cuerpo, notaPie);
            subject = "Restablecimiento de Contraseña Administrativa - SAAS MANAGER";
        } else {
            String coopName = obtenerNombreCoop(admin.getEmpresaId());
            htmlBody = templateBuilder.buildCorporateEmail(titulo, mensaje, cuerpo, notaPie, coopName);
            subject = "Restablecimiento de Contraseña Administrativa - " + coopName;
        }

        enviarCorreoResend(admin.getCorreo(), subject, htmlBody);
    }

    /**
     * Envía las credenciales de acceso inicial a los dueños de nuevos Tenants (Cooperativas)
     */
    public void enviarCredencialesSaaS(String to, String razonSocial, String usuario, String linkActivacion) {
        String titulo = "¡Bienvenido al Core Bancario SaaS!";
        String mensaje = "Estimado/a Representante de " + razonSocial + ", su instancia de cooperativa ha sido creada exitosamente. A continuación su nombre de usuario de acceso:";
        
        String cuerpo = "<div style=\"background-color:#f8fafc; padding:15px; border-radius:10px; text-align:center;\">"
                      + "<p style=\"margin:0; font-size:14px; color:#475569;\"><strong>Usuario Administrador:</strong> " + usuario + "</p>"
                      + "</div><br/>"
                      + "<p style=\"font-size:13px; color:#475569;\">Por favor, haga clic en el botón de abajo para activar su cuenta y configurar su contraseña digital de seguridad:</p>"
                      + templateBuilder.buildLinkButton(linkActivacion, "Activar Cuenta y Establecer Clave");
        
        String notaPie = "Este enlace de activación es de un solo uso y expirará en 15 minutos.";
        String htmlBody = templateBuilder.buildSaaSEmail(titulo, mensaje, cuerpo, notaPie);

        enviarCorreoResend(to, "Bienvenida y Activación de Cuenta SaaS - " + razonSocial, htmlBody);
    }

    /**
     * Simula el envío de mensaje de texto con código OTP.
     */
    public void enviarRecuperacionSms(Socio socio, String otp) {
        log.info("=========================================================================");
        log.info("[NotificacionService] SIMULACIÓN SMS");
        log.info("Para el número: {}", socio.getTelefono());
        log.info("Mensaje: CoopApp Informa: Su código OTP de recuperación es {}. Válido por 15 minutos. No lo comparta.", otp);
        log.info("=========================================================================");
    }
}
