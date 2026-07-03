package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@Entity
@Table(name = "cuotas_amortizacion", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"credito_id", "numero_cuota"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CuotasAmortizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credito_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Credito credito;

    @Column(name = "numero_cuota", nullable = false)
    private Integer numeroCuota;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    // Componentes Proyectados (La promesa original de pago)
    @Column(name = "capital_proyectado", nullable = false)
    private BigDecimal capitalProyectado;

    @Column(name = "interes_proyectado", nullable = false)
    private BigDecimal interesProyectado;

    // Columna calculada de forma determinista en PostgreSQL (Solo lectura en Java)
    @Column(name = "cuota_total_proyectada", insertable = false, updatable = false)
    private BigDecimal cuotaTotalProyectada;

    // Componentes Reales Pagados por el Socio
    @Column(name = "capital_pagado", nullable = false)
    private BigDecimal capitalPagado = BigDecimal.ZERO;

    @Column(name = "interes_pagado", nullable = false)
    private BigDecimal interesPagado = BigDecimal.ZERO;

    // Campos de control para la fórmula de Mora (Proceso Batch)
    @Column(name = "monto_mora_acumulado", nullable = false)
    private BigDecimal montoMoraAcumulado = BigDecimal.ZERO;

    @Column(name = "monto_mora_pagado", nullable = false)
    private BigDecimal montoMoraPagado = BigDecimal.ZERO;

    @Column(name = "dias_atraso", nullable = false)
    private Integer diasAtraso = 0;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE"; // 'PENDIENTE', 'PAGADA', 'EN_MORA'

    @Column(name = "fecha_ultimo_pago")
    private LocalDateTime fechaUltimoPago;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}