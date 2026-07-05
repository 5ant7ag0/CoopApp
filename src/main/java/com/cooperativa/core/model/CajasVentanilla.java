package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "cajas_ventanilla",
    uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "codigo"})
)
public class CajasVentanilla extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 50)
    private String nombre;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agencia_id")
    private Agencia agencia;

    @Column(name = "saldo_base", precision = 19, scale = 4)
    private java.math.BigDecimal saldoBase = java.math.BigDecimal.ZERO;

    @Column(name = "saldo_actual", precision = 19, scale = 4)
    private java.math.BigDecimal saldoActual = java.math.BigDecimal.ZERO;

    @Column(name = "limite_efectivo_maximo", precision = 19, scale = 4)
    private java.math.BigDecimal limiteEfectivoMaximo = java.math.BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_contable_id")
    private PlanCuentas cuentaContable;

    @PrePersist
    public void setDefaults() {
        if (this.getEstado() == null) {
            this.setEstado("ACTIVA");
        }
    }
}
