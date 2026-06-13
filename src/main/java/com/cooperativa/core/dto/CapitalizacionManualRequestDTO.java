package com.cooperativa.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CapitalizacionManualRequestDTO {

    @NotNull(message = "El año es obligatorio")
    @Min(value = 2000, message = "El año debe ser válido")
    @Max(value = 2100, message = "El año debe ser válido")
    private Integer anio;

    @NotNull(message = "El mes es obligatorio")
    @Min(value = 1, message = "El mes debe ser entre 1 y 12")
    @Max(value = 12, message = "El mes debe ser entre 1 y 12")
    private Integer mes;
}
