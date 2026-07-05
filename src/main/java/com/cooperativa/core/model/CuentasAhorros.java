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
public class CuentasAhorros extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_ahorro_id")
    private ProductoAhorro productoAhorro;

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

    @PrePersist
    public void setDefaults() {
        if (this.getEstado() == null) {
            this.setEstado("ACTIVA");
        }
    }

    @Column(name = "plazo_dias")
    private Integer plazoDias;

    @Column(name = "fecha_vencimiento")
    private java.time.LocalDate fechaVencimiento;

    @Column(name = "renovacion_automatica")
    private Boolean renovacionAutomatica = false;

}