package com.cooperativa.core.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO para representar una cuota simulada de amortizacion.
 */
@Getter
@Setter
@AllArgsConstructor
public class CuotaSimuladaDTO {
    private int numeroCuota;
    private LocalDate fechaVencimiento;
    private BigDecimal capital;
    private BigDecimal interes;
    private BigDecimal cuotaTotal;
    private BigDecimal saldoRemanente;
}