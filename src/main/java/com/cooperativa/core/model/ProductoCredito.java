package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@Entity
@Table(name = "productos_credito")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ProductoCredito extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "monto_minimo", nullable = false)
    private BigDecimal montoMinimo = BigDecimal.ZERO;

    @Column(name = "monto_maximo", nullable = false)
    private BigDecimal montoMaximo = BigDecimal.ZERO;

    @Column(name = "plazo_minimo_meses", nullable = false)
    private Integer plazoMinimoMeses;

    @Column(name = "plazo_maximo_meses", nullable = false)
    private Integer plazoMaximoMeses;

    @Column(name = "tasa_interes_anual", nullable = false)
    private BigDecimal tasaInteresAnual;

    @Column(name = "tasa_mora_anual", nullable = false)
    private BigDecimal tasaMoraAnual;

    @Column(name = "porcentaje_seguro_desgravamen", nullable = false)
    private BigDecimal porcentajeSeguroDesgravamen = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_cartera_id", nullable = false)
    private PlanCuentas cuentaContableCartera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_ingresos_intereses_id", nullable = false)
    private PlanCuentas cuentaContableIngresosIntereses;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_mora_id", nullable = false)
    private PlanCuentas cuentaContableMora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_seguro_id")
    private PlanCuentas cuentaContableSeguro;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO"; // 'ACTIVO', 'INACTIVO'

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
