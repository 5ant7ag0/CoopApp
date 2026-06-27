package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CuentasAhorrosRequestDTO {

    @NotNull(message = "El id del socio es obligatorio")
    private Integer socioId;

    @NotBlank(message = "El numero de cuenta es obligatorio")
    private String numeroCuenta;

    @NotBlank(message = "El tipo de cuenta es obligatorio")
    private String tipo; // 'AHORRO_VISTA' o 'APORTACIONES'

    private Integer productoAhorroId;

    private String estado;
}
