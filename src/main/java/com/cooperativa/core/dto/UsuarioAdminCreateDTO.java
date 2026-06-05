package com.cooperativa.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para la creacion de nuevos usuarios administrativos (Backoffice).
 */
@Getter
@Setter
public class UsuarioAdminCreateDTO {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 4, max = 50, message = "El usuario debe tener entre 4 y 50 caracteres")
    private String username;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(min = 6, message = "La contrasena debe tener al menos 6 caracteres")
    private String password;

    @NotBlank(message = "Los nombres completos son obligatorios")
    @Size(max = 100, message = "El nombre completo no puede superar los 100 caracteres")
    private String nombresCompletos;

    @NotBlank(message = "El correo electronico es obligatorio")
    @Email(message = "El correo electronico debe ser valido")
    @Size(max = 100, message = "El correo no puede superar los 100 caracteres")
    private String correo;

    @NotBlank(message = "El rol es obligatorio")
    private String rol; // 'SUPER_ADMIN_SAAS', 'GERENTE_GENERAL', 'OFICIAL_DE_CREDITO', 'CAJERO', 'AUDITOR_INTERNO'
}
