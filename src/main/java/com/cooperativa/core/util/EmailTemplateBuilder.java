package com.cooperativa.core.util;

import org.springframework.stereotype.Component;

@Component
public class EmailTemplateBuilder {

    /**
     * Construye la plantilla HTML corporativa inyectando el contenido dinámico.
     * 
     * @param titulo   El título central del correo (ej: "Verificación de Correo")
     * @param mensaje  El texto principal o saludo.
     * @param cuerpo   El contenido dinámico central (puede ser un OTP grande o un botón de enlace).
     * @param notaPie  Nota aclaratoria en texto pequeño (ej: validez del token).
     * @return String con el HTML completo listo para enviar.
     */
    public String buildCorporateEmail(String titulo, String mensaje, String cuerpo, String notaPie) {
        return String.format(
            "<!DOCTYPE html><html><head><style>" +
            "body { font-family: Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 20px; }" +
            ".card { max-width: 500px; margin: 0 auto; background: white; border-radius: 16px; padding: 30px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }" +
            ".header { text-align: center; border-bottom: 2px solid #0054A6; padding-bottom: 15px; margin-bottom: 25px; }" +
            ".logo { font-size: 20px; font-weight: bold; color: #0054A6; margin: 0; }" +
            ".title { font-size: 18px; font-weight: bold; margin-bottom: 15px; color: #0f172a; }" +
            ".content-block { text-align: center; margin: 25px 0; }" +
            ".otp { display: inline-block; background-color: #f1f5f9; color: #0054A6; font-size: 24px; font-weight: bold; letter-spacing: 4px; padding: 12px 30px; border-radius: 12px; border: 1px dashed #cbd5e1; margin: 10px 0; }" +
            ".btn { display: inline-block; background-color: #0054A6; color: white; font-weight: bold; text-decoration: none; padding: 12px 24px; border-radius: 8px; margin: 10px 0; }" +
            ".footer { text-align: center; font-size: 11px; color: #94a3b8; margin-top: 30px; border-top: 1px solid #f1f5f9; padding-top: 15px; }" +
            "</style></head><body><div class=\"card\">" +
            "<div class=\"header\"><div class=\"logo\">COOPERATIVA DE AHORRO Y CRÉDITO ITQ</div></div>" +
            "<div class=\"title\">%s</div>" +
            "<p>%s</p>" +
            "<div class=\"content-block\">%s</div>" +
            "<p style=\"font-size: 12px; color: #64748b;\">%s</p>" +
            "<div class=\"footer\">Este es un mensaje automático de la Cooperativa ITQ Ltda. Por favor no responda a este correo.</div>" +
            "</div></body></html>",
            titulo, mensaje, cuerpo, notaPie
        );
    }

    /**
     * Construye un bloque OTP para inyectar en el cuerpo.
     */
    public String buildOtpBlock(String otpCode) {
        return String.format("<div class=\"otp\">%s</div>", otpCode);
    }

    /**
     * Construye un botón de enlace para inyectar en el cuerpo.
     */
    public String buildLinkButton(String url, String buttonText) {
        return String.format("<a href=\"%s\" class=\"btn\">%s</a>", url, buttonText);
    }
}
