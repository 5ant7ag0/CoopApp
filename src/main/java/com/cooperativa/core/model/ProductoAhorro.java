package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "productos_ahorro")
public class ProductoAhorro extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "tipo_producto", nullable = false, length = 50)
    private String tipoProducto; // 'AHORRO_VISTA', 'AHORRO_PROGRAMADO', 'PLAZO_FIJO', 'APORTACIONES'

    @Column(name = "tasa_interes_anual", nullable = false)
    private BigDecimal tasaInteresAnual = BigDecimal.ZERO;

    @Column(name = "monto_minimo_apertura", nullable = false)
    private BigDecimal montoMinimoApertura = BigDecimal.ZERO;

    @Column(name = "saldo_minimo_requerido", nullable = false)
    private BigDecimal saldoMinimoRequerido = BigDecimal.ZERO;

    @Column(name = "tipo_retiro", nullable = false, length = 20)
    private String tipoRetiro = "LIBRE"; // 'LIBRE', 'PENALIZADO', 'RESTRINGIDO'

    @Column(name = "tasa_penalizacion_retiro", nullable = false)
    private BigDecimal tasaPenalizacionRetiro = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_pasivo_id", nullable = false)
    private PlanCuentas cuentaContablePasivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_gasto_id", nullable = false)
    private PlanCuentas cuentaContableGasto;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO"; // 'ACTIVO', 'INACTIVO'

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
