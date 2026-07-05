package com.cooperativa.core.dto;

import com.cooperativa.core.model.TenantEstado;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class TenantDetailDTO {
    private Integer id;
    private String ruc;
    private String razonSocial;
    private String nombreComercial;
    private String representanteLegal;
    private String cedulaRepresentante;
    private String correoInstitucional;
    private String direccion;
    private String telefono;
    private String codigoSeps;
    private String segmentoSeps;
    private TenantEstado estado;
    private Integer limiteUsuariosAdmin;
    private Integer limiteSocios;
    private long totalUsuarios;
    private long totalSocios;
    private String logoUrl;
    private String siglas;
    private String correoGerente;
    private LocalDateTime createdAt;
}
