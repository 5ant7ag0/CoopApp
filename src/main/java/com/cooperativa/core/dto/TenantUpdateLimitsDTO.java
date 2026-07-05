package com.cooperativa.core.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantUpdateLimitsDTO {

    @Min(value = 1, message = "El límite de usuarios administrativos debe ser al menos 1")
    private Integer limiteUsuariosAdmin;

    @Min(value = 0, message = "El límite de socios no puede ser negativo")
    private Integer limiteSocios;
}
