package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "cierres_anuales", uniqueConstraints = {
        @UniqueConstraint(name = "uk_empresa_anio", columnNames = {"empresa_id", "anio_fiscal"})
})
public class CierreAnual extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "anio_fiscal", nullable = false)
    private Integer anioFiscal;

    @Column(name = "total_ingresos", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIngresos;

    @Column(name = "total_gastos", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalGastos;

    @Column(name = "total_provisiones", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalProvisiones = BigDecimal.ZERO;

    @Column(name = "excedente_neto", nullable = false, precision = 15, scale = 2)
    private BigDecimal excedenteNeto;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre = LocalDateTime.now();

    @Column(name = "usuario_admin_id", nullable = false)
    private Integer usuarioAdminId;
}
