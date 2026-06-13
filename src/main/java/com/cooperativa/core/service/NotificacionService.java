package com.cooperativa.core.service;

import com.cooperativa.core.model.Socio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    /**
     * Simula el envío de correo con enlace de recuperación seguro.
     */
    public void enviarRecuperacionCorreo(Socio socio, String token) {
        log.info("=========================================================================");
        log.info("[NotificacionService] SIMULACIÓN CORREO ELECTRÓNICO");
        log.info("Para: {}", socio.getCorreo());
        log.info("Asunto: Restablecimiento de Contraseña Digital - CoopApp");
        log.info("Estimado/a {},", socio.getNombresCompletos());
        log.info("Hemos recibido una solicitud para cambiar su contraseña digital.");
        log.info("Para continuar, haga clic en el siguiente enlace de recuperación:");
        log.info("http://localhost:5173/recuperar-clave?token={}", token);
        log.info("Este enlace es de un solo uso y expirará en 15 minutos.");
        log.info("=========================================================================");
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
