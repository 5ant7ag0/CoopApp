package com.cooperativa.core.dto;

import com.cooperativa.core.model.TenantEstado;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantListDTO {
    private Integer id;
    private String ruc;
    private String razonSocial;
    private String nombreComercial;
    private String representanteLegal;
    private String correoInstitucional;
    private TenantEstado estado;
    private Integer limiteUsuariosAdmin;
    private Integer limiteSocios;
    private long totalUsuarios;
    private long totalSocios;
    private java.time.LocalDateTime createdAt;
}
