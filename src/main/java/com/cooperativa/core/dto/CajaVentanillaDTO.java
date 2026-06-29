package com.cooperativa.core.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CajaVentanillaDTO {
    private Integer id;
    private String codigo;
    private String nombre;
    private Integer agenciaId;
    private String agenciaNombre;
    private BigDecimal saldoBase;
    private BigDecimal saldoActual;
    private BigDecimal limiteEfectivoMaximo;
    private Integer cuentaContableId;
    private String cuentaContableNombre;
    private String estado;
    private LocalDateTime createdAt;
    
    // Additional fields for frontend display
    private String cajeroAsignado; // To show who is assigned currently if needed
    private String estadoOperativo; // 'ABIERTA' or 'CERRADA'
}
