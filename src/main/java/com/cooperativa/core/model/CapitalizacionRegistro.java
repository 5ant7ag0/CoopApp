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
@Table(name = "capitalizaciones_registro", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "anio", "mes"})
})
public class CapitalizacionRegistro extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Integer anio;

    @Column(nullable = false)
    private Integer mes;

    @Column(name = "fecha_capitalizacion", nullable = false)
    private LocalDate fechaCapitalizacion;

    @Column(name = "total_capitalizado", nullable = false)
    private BigDecimal totalCapitalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_cabecera_id")
    private AsientosCabecera asientoCabecera;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
