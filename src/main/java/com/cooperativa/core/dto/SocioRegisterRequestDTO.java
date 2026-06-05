package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para que los socios registren sus credenciales de acceso por primera vez.
 */
@Getter
@Setter
public class SocioRegisterRequestDTO {

    @NotBlank(message = "La identificacion del socio es obligatoria")
    private String identificacion;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
    private String password;
}
