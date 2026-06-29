package com.cooperativa.core.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AgenciaDTO {
    private Integer id;
    private String codigo;
    private String nombre;
    private String direccion;
    private String estado;
    private LocalDateTime createdAt;
}
