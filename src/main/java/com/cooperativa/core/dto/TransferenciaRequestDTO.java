package com.cooperativa.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * DTO inmutable para capturar la solicitud de transferencia interna entre cuentas de la misma cooperativa.
 */
@Getter
@Setter
public class TransferenciaRequestDTO {

    @NotNull(message = "El ID de la cuenta de origen es obligatorio")
    private Integer cuentaOrigenId;

    @NotNull(message = "El ID de la cuenta de destino es obligatorio")
    private Integer cuentaDestinoId;

    @NotNull(message = "El monto a transferir es obligatorio")
    @Positive(message = "El monto a transferir debe ser mayor a cero")
    @DecimalMin(value = "0.01", message = "El monto a transferir debe ser de al menos $0.01")
    @Digits(integer = 15, fraction = 2, message = "El monto a transferir debe tener un formato decimal de máximo 15 enteros y 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "El concepto de la transferencia es obligatorio")
    private String concepto;
}
