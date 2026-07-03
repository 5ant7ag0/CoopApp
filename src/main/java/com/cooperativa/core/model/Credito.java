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
@Table(name = "creditos", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "numero_credito"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Credito extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false)
    private Socio socio;

    @Column(name = "numero_credito", nullable = false, length = 20)
    private String numeroCredito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_credito_id")
    private ProductoCredito productoCredito;

    @Column(name = "monto_solicitado", nullable = false)
    private BigDecimal montoSolicitado;

    @Column(name = "monto_desembolsado")
    private BigDecimal montoDesembolsado = BigDecimal.ZERO;

    @Column(name = "plazo_meses", nullable = false)
    private Integer plazoMeses;

    @Column(name = "tasa_interes_anual", nullable = false)
    private BigDecimal tasaInteresAnual;

    @Column(name = "tasa_mora_anual", nullable = false)
    private BigDecimal tasaMoraAnual;

    @Column(name = "tipo_amortizacion", nullable = false, length = 20)
    private String tipoAmortizacion; // 'FRANCES', 'ALEMAN', 'AMERICANO'

    @Column(name = "garantia_descripcion", nullable = false, columnDefinition = "TEXT")
    private String garantiaDescripcion;

    @Column(nullable = false, length = 20)
    private String estado = "SOLICITADO"; // 'SOLICITADO', 'EN_REVISION', 'DESEMBOLSADO', etc.

    @Column(name = "fecha_solicitud", updatable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "fecha_desembolso")
    private LocalDateTime fechaDesembolso;

    @Column(name = "usuario_oficial_id")
    private Integer usuarioOficialId; // Oficial de crédito asignado

    @Column(name = "motivo_rechazo", columnDefinition = "TEXT")
    private String motivoRechazo;

    @OneToMany(mappedBy = "credito", fetch = FetchType.LAZY)
    @OrderBy("numeroCuota ASC")
    private java.util.List<CuotasAmortizacion> cuotas;
}