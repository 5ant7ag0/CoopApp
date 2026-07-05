package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "asientos_cabecera")
@org.hibernate.annotations.Immutable
public class AsientosCabecera extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaccion_ledger_id")
    private TransaccionesLedger transaccionLedger;

    @Column(name = "numero_asiento", nullable = false, length = 30)
    private String numeroAsiento; // Correlativo único (Ej: AS-2026-0001)

    @Column(nullable = false, columnDefinition = "TEXT")
    private String glosa; // Descripción global de la transacción doble

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "fecha_asiento", nullable = false, updatable = false)
    private LocalDateTime fechaAsiento = LocalDateTime.now();

    @OneToMany(mappedBy = "asientoCabecera", fetch = FetchType.LAZY)
    private java.util.List<AsientosDetalle> detalles;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}