package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "cajas_diarias", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "usuario_cajero_id", "fecha_contable"})
})
public class CajaDiaria extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cajero_id", nullable = false)
    private UsuariosAdmin usuarioCajero;

    @Column(name = "fecha_contable", nullable = false)
    private LocalDate fechaContable;

    @Column(name = "monto_apertura", nullable = false)
    private BigDecimal montoApertura;

    @Column(name = "monto_cierre_sistema", nullable = false)
    private BigDecimal montoCierreSistema = BigDecimal.ZERO;

    @Column(name = "monto_cierre_efectivo_real")
    private BigDecimal montoCierreEfectivoReal;

    @Column(nullable = false)
    private BigDecimal diferencia = BigDecimal.ZERO;

    @Column(nullable = false, length = 15)
    private String estado = "APERTURADA"; // 'APERTURADA', 'CERRADA'

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_cabecera_id")
    private AsientosCabecera asientoCabecera;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdateCaja() {
        super.preUpdate();
        this.updatedAt = LocalDateTime.now();
    }
}
