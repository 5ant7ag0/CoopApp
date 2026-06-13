package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para la solicitud de cambio de clave digital de un socio.
 */
@Getter
@Setter
public class CambioClaveRequestDTO {

    @NotBlank(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    private String passwordNueva;
}
