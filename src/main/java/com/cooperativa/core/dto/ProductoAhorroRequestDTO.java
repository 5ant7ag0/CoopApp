package com.cooperativa.core.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductoAhorroRequestDTO {

    @NotBlank(message = "El nombre del producto es obligatorio")
    private String nombre;

    @NotBlank(message = "El tipo de producto es obligatorio")
    private String tipoProducto; // 'AHORRO_VISTA', 'AHORRO_PROGRAMADO', 'PLAZO_FIJO', 'APORTACIONES'

    @NotNull(message = "La tasa de interés anual es obligatoria")
    @DecimalMin(value = "0.00", message = "La tasa de interés no puede ser menor a 0.00%")
    @DecimalMax(value = "30.00", message = "La tasa de interés no puede ser mayor a 30.00%")
    private BigDecimal tasaInteresAnual;

    @NotNull(message = "El monto mínimo de apertura es obligatorio")
    @DecimalMin(value = "0.00", message = "El monto mínimo no puede ser negativo")
    private BigDecimal montoMinimoApertura;

    @NotNull(message = "El saldo mínimo requerido es obligatorio")
    @DecimalMin(value = "0.00", message = "El saldo mínimo no puede ser negativo")
    private BigDecimal saldoMinimoRequerido;

    @NotBlank(message = "El tipo de retiro es obligatorio")
    private String tipoRetiro; // 'LIBRE', 'PENALIZADO', 'RESTRINGIDO'

    @NotNull(message = "La tasa de penalización es obligatoria")
    @DecimalMin(value = "0.00", message = "La tasa de penalización no puede ser negativa")
    private BigDecimal tasaPenalizacionRetiro;

    @NotNull(message = "La cuenta contable de pasivo es obligatoria")
    private Integer cuentaContablePasivoId;

    @NotNull(message = "La cuenta contable de gasto es obligatoria")
    private Integer cuentaContableGastoId;

    private String estado; // 'ACTIVO', 'INACTIVO'
}
