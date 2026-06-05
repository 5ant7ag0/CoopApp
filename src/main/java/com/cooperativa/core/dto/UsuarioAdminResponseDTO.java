package com.cooperativa.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO para representar de forma segura la informacion de un usuario administrativo.
 * Evita exponer hashes de contrasena.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioAdminResponseDTO {
    private Integer id;
    private Integer empresaId;
    private String username;
    private String nombresCompletos;
    private String correo;
    private String rol;
    private String estado;
}
