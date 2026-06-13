package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para capturar la solicitud de recuperacion de clave.
 */
@Getter
@Setter
public class SolicitudRecuperacionDTO {

    @NotBlank(message = "La identificacion del socio es obligatoria")
    private String identificacion;

    @NotBlank(message = "El canal es obligatorio")
    @Pattern(regexp = "CORREO|SMS", message = "El canal debe ser CORREO o SMS")
    private String canal;
}
