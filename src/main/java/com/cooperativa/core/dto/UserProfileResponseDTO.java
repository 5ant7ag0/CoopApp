package com.cooperativa.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponseDTO {
    private String username;
    private String nombresCompletos;
    private String rol;
    private Integer empresaId;
    private Object detalles; // Contiene la entidad Socio o UsuariosAdmin para el perfil
}
