package com.cooperativa.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * DTO para capturar la solicitud de pago de una cuota de credito.
 */
@Getter
@Setter
public class PagoRequestDTO {

    @NotNull(message = "El ID del credito es obligatorio")
    private Integer creditoId;

    @NotNull(message = "El ID de la cuenta de ahorros de origen es obligatorio")
    private Integer cuentaAhorrosId;

    @NotNull(message = "El monto a pagar es obligatorio")
    @Positive(message = "El monto a pagar debe ser mayor a cero")
    @DecimalMin(value = "0.01", message = "El monto a pagar debe ser de al menos $0.01")
    @Digits(integer = 15, fraction = 2, message = "El monto a pagar debe tener un formato decimal de máximo 15 enteros y 2 decimales")
    private BigDecimal monto;
}