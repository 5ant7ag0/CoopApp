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
@Table(name = "devengos_registro", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "fecha_devengo"})
})
public class DevengoRegistro extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "fecha_devengo", nullable = false)
    private LocalDate fechaDevengo;

    @Column(name = "total_devengado", nullable = false)
    private BigDecimal totalDevengado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_cabecera_id")
    private AsientosCabecera asientoCabecera;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
