package com.cooperativa.core.service;

import com.cooperativa.core.model.Socio;
import com.cooperativa.core.util.EmailTemplateBuilder;
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
        String htmlBody = templateBuilder.buildCorporateEmail(titulo, mensaje, cuerpo, notaPie);

        enviarCorreoResend(socio.getCorreo(), "Restablecimiento de Contraseña Digital - CoopApp", htmlBody);
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
