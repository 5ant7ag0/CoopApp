package com.cooperativa.core.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO para la solicitud de desembolso de credito.
 */
@Getter
@Setter
public class DesembolsoRequestDTO {
    private Integer creditoId;
    private Integer cuentaAhorrosId;
}