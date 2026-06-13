package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "asientos_detalle")
public class AsientosDetalle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_cabecera_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private AsientosCabecera asientoCabecera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_cuentas_id", nullable = false)
    private PlanCuentas planCuentas;

    @Column(name = "tipo_asiento", nullable = false, length = 7)
    private String tipoAsiento; // 'DEBITO' (Debe) o 'CREDITO' (Haber)

    @Column(nullable = false)
    private BigDecimal monto;
}