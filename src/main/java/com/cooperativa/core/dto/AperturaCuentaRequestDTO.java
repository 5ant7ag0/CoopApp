package com.cooperativa.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AperturaCuentaRequestDTO {

    @NotNull(message = "El socioId es obligatorio.")
    private Integer socioId;

    @NotNull(message = "El productoAhorroId es obligatorio.")
    private Integer productoAhorroId;

    @NotNull(message = "El montoInicial es obligatorio.")
    @DecimalMin(value = "0.00", message = "El monto inicial no puede ser negativo.")
    private BigDecimal montoInicial;

    @NotBlank(message = "El medio de fondeo es obligatorio.")
    private String medioFondeo; // 'TRANSFERENCIA' o 'VENTANILLA'
}
