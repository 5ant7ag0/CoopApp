package com.cooperativa.core.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class CierreCajaDTO {

    @NotNull(message = "El monto de cierre efectivo real es obligatorio")
    @DecimalMin(value = "0.00", message = "El monto de cierre real no puede ser negativo")
    @Digits(integer = 15, fraction = 2, message = "El monto debe tener máximo 15 enteros y 2 decimales")
    private BigDecimal montoCierreEfectivoReal;
}
