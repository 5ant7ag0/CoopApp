package com.cooperativa.core.controller;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.OtpVerificacion;
import com.cooperativa.core.repository.OtpVerificacionRepository;
import com.cooperativa.core.service.ResendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.cooperativa.core.security.RequiresRoles;

@RestController
@RequestMapping("/otp")
@CrossOrigin(origins = "*")
@RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
public class OtpController {

    @Autowired
    private OtpVerificacionRepository otpVerificacionRepository;

    @Autowired
    private ResendService resendService;

    @PostMapping("/enviar")
    public ResponseEntity<?> enviarOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El correo electrónico es obligatorio.");
        }

        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return ResponseEntity.status(401).body("Error de Seguridad: No se encontró el X-Tenant-ID.");
        }

        // Generar OTP de 6 dígitos
        Random random = new Random();
        int code = 100000 + random.nextInt(900000);
        String otpCode = String.valueOf(code);

        // Guardar OTP en BD
        OtpVerificacion otp = new OtpVerificacion();
        otp.setEmpresaId(tenantId);
        otp.setEmail(email.trim().toLowerCase());
        otp.setOtpCode(otpCode);
        otp.setFechaExpiracion(LocalDateTime.now().plusMinutes(5));
        otp.setVerificado(false);
        otp.setIntentosFallidos(0);

        otpVerificacionRepository.save(otp);

        // Cuerpo HTML Corporativo
        String htmlBody = String.format(
            "<!DOCTYPE html><html><head><style>" +
            "body { font-family: Arial, sans-serif; background-color: #f8fafc; color: #1e293b; padding: 20px; }" +
            ".card { max-width: 500px; margin: 0 auto; background: white; border-radius: 16px; padding: 30px; border: 1px solid #e2e8f0; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }" +
            ".header { text-align: center; border-bottom: 2px solid #0054A6; padding-bottom: 15px; margin-bottom: 25px; }" +
            ".logo { font-size: 20px; font-weight: bold; color: #0054A6; margin: 0; }" +
            ".title { font-size: 18px; font-weight: bold; margin-bottom: 15px; color: #0f172a; }" +
            ".otp { display: inline-block; background-color: #f1f5f9; color: #0054A6; font-size: 24px; font-weight: bold; letter-spacing: 4px; padding: 12px 30px; border-radius: 12px; border: 1px dashed #cbd5e1; margin: 20px 0; }" +
            ".footer { text-align: center; font-size: 11px; color: #94a3b8; margin-top: 30px; border-top: 1px solid #f1f5f9; padding-top: 15px; }" +
            "</style></head><body><div class=\"card\">" +
            "<div class=\"header\"><div class=\"logo\">COOPERATIVA DE AHORRO Y CRÉDITO ITQ</div></div>" +
            "<div class=\"title\">Verificación de Correo Electrónico</div>" +
            "<p>Estimado usuario,</p>" +
            "<p>Utilice el siguiente código de verificación de 6 dígitos para validar su correo electrónico en el proceso de registro de socio:</p>" +
            "<div style=\"text-align: center;\"><div class=\"otp\">%s</div></div>" +
            "<p style=\"font-size: 12px; color: #64748b;\">Este código es de uso único y tiene una validez de 5 minutos. No comparta este código con nadie.</p>" +
            "<div class=\"footer\">Este es un mensaje automático de la Cooperativa ITQ Ltda. Por favor no responda a este correo.</div>" +
            "</div></body></html>",
            otpCode
        );

        resendService.enviarCorreo(email.trim().toLowerCase(), "Código de Verificación OTP - Cooperativa", htmlBody);

        return ResponseEntity.ok(Map.of("message", "Código OTP enviado exitosamente."));
    }

    @PostMapping("/validar")
    public ResponseEntity<?> validarOtp(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String code = request.get("code");

        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El correo electrónico es obligatorio.");
        }
        if (code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("El código OTP es obligatorio.");
        }

        Integer tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            return ResponseEntity.status(401).body("Error de Seguridad: No se encontró el X-Tenant-ID.");
        }

        LocalDateTime ahora = LocalDateTime.now();
        Optional<OtpVerificacion> otpOpt = otpVerificacionRepository
            .findFirstByEmailAndEmpresaIdAndVerificadoFalseAndFechaExpiracionAfterOrderByCreatedAtDesc(
                email.trim().toLowerCase(), tenantId, ahora
            );

        if (otpOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("El código OTP es inválido o ha expirado.");
        }

        OtpVerificacion otp = otpOpt.get();

        if (otp.getIntentosFallidos() >= 3) {
            return ResponseEntity.badRequest().body("El código OTP ha sido bloqueado por superar el límite de intentos fallidos.");
        }

        if (!otp.getOtpCode().equals(code.trim())) {
            otp.setIntentosFallidos(otp.getIntentosFallidos() + 1);
            otpVerificacionRepository.save(otp);
            if (otp.getIntentosFallidos() >= 3) {
                return ResponseEntity.badRequest().body("El código OTP ha sido bloqueado por superar el límite de intentos fallidos.");
            }
            return ResponseEntity.badRequest().body("El código OTP ingresado es incorrecto.");
        }

        otp.setVerificado(true);
        otpVerificacionRepository.save(otp);

        return ResponseEntity.ok(Map.of("message", "Correo electrónico verificado exitosamente."));
    }
}
