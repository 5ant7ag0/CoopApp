package com.cooperativa.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class TransaccionVentanillaDTO {

    @NotNull(message = "El ID de la cuenta es obligatorio")
    private Integer cuentaAhorrosId;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser de al menos $0.01")
    @Digits(integer = 15, fraction = 2, message = "El monto debe tener máximo 15 enteros y 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "El concepto de la transacción es obligatorio")
    private String concepto;

    // Control UAFE/SEPS: Declaración de Origen de Fondos firmada para transacciones > $10,000.00 USD
    private Boolean declaracionOrigenFondos = false;
}
