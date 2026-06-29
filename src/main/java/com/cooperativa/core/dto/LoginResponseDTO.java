package com.cooperativa.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para retornar la respuesta de un login exitoso, incluyendo el token JWT.
 */
@Getter
@Setter
@AllArgsConstructor
public class LoginResponseDTO {
    private String token;
    private String username;
    private String nombresCompletos;
    private String rol;
    private Integer empresaId;
    private boolean cambiarPasswordRequerido;

    public LoginResponseDTO(String token, String username, String nombresCompletos, String rol, Integer empresaId) {
        this.token = token;
        this.username = username;
        this.nombresCompletos = nombresCompletos;
        this.rol = rol;
        this.empresaId = empresaId;
        this.cambiarPasswordRequerido = false;
    }
}
