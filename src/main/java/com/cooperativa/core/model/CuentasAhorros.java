package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "cuentas_ahorros", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "numero_cuenta"})
})
public class CuentasAhorros extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @Column(name = "numero_cuenta", nullable = false, length = 20)
    private String numeroCuenta;

    @Column(nullable = false, length = 30)
    private String tipo; // 'AHORRO_VISTA' o 'APORTACIONES'

    @Column(nullable = false)
    private BigDecimal saldo = BigDecimal.ZERO;

    @Column(name = "tasa_interes_anual", nullable = false)
    private BigDecimal tasaInteresAnual = BigDecimal.ZERO;

    @Column(name = "interes_acumulado", nullable = false)
    private BigDecimal interesAcumulado = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVA";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}