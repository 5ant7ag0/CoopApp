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
}
