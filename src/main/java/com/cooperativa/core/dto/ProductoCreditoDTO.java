package com.cooperativa.core.dto;

import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class ProductoCreditoDTO {
    private Integer id;
    private String nombre;
    private BigDecimal montoMinimo;
    private BigDecimal montoMaximo;
    private Integer plazoMinimoMeses;
    private Integer plazoMaximoMeses;
    private BigDecimal tasaInteresAnual;
    private BigDecimal tasaMoraAnual;
    private BigDecimal porcentajeSeguroDesgravamen;
    private Integer cuentaContableCarteraId;
    private Integer cuentaContableIngresosInteresesId;
    private Integer cuentaContableMoraId;
    private Integer cuentaContableSeguroId;
    private String estado;
}
