package com.cooperativa.core.controller;

import com.cooperativa.core.config.TenantContext;
import com.cooperativa.core.model.OtpVerificacion;
import com.cooperativa.core.repository.OtpVerificacionRepository;
import com.cooperativa.core.repository.EmpresaRepository;
import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.service.ResendService;
import com.cooperativa.core.util.EmailTemplateBuilder;
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

@RequiresRoles({"OFICIAL_DE_CREDITO", "GERENTE_GENERAL", "SUPER_ADMIN_SAAS"})
public class OtpController {

    @Autowired
    private OtpVerificacionRepository otpVerificacionRepository;

    @Autowired
    private ResendService resendService;

    @Autowired
    private EmailTemplateBuilder emailTemplateBuilder;

    @Autowired
    private EmpresaRepository empresaRepository;

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

        String coopName = empresaRepository.findById(tenantId)
            .map(Empresa::getNombreComercial)
            .orElse("COOPERATIVA DE AHORRO Y CRÉDITO");

        // Cuerpo HTML Corporativo generado dinámicamente
        String cuerpoOtp = emailTemplateBuilder.buildOtpBlock(otpCode);
        String htmlBody = emailTemplateBuilder.buildCorporateEmail(
            "Verificación de Correo Electrónico",
            "Estimado usuario,<br/><br/>Utilice el siguiente código de verificación de 6 dígitos para validar su correo electrónico en el proceso de registro de socio:",
            cuerpoOtp,
            "Este código es de uso único y tiene una validez de 5 minutos. No comparta este código con nadie.",
            coopName
        );

        resendService.enviarCorreo(email.trim().toLowerCase(), "Código de Verificación OTP - " + coopName, htmlBody);

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
