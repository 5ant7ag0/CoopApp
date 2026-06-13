package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para validar el token y cambiar la clave.
 */
@Getter
@Setter
public class RestablecerClaveRequestDTO {

    @NotBlank(message = "La identificacion del socio es obligatoria")
    private String identificacion;

    @NotBlank(message = "El token o codigo OTP es obligatorio")
    private String token;

    @NotBlank(message = "La nueva contrasena es obligatoria")
    @Size(min = 6, message = "La nueva contrasena debe tener al menos 6 caracteres")
    private String passwordNueva;
}
