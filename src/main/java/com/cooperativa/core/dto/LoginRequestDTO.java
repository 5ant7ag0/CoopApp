package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para capturar las credenciales de inicio de sesion.
 * Funciona tanto para administradores (username) como para socios (identificacion).
 */
@Getter
@Setter
public class LoginRequestDTO {

    @NotBlank(message = "El identificador (usuario/cedula) es obligatorio")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    private String password;
}
