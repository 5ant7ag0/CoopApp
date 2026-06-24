package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "empresas")
public class Empresa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 13)
    private String ruc;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "nombre_comercial", nullable = false, length = 150)
    private String nombreComercial;

    @Column(name = "codigo_seps", nullable = false, unique = true, length = 50)
    private String codigoSeps;

    @Column(name = "representante_legal", nullable = false, length = 100)
    private String representanteLegal;

    @Column(name = "cedula_representante", nullable = false, length = 10)
    private String cedulaRepresentante;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(length = 3)
    private String moneda = "USD";

    @Column(length = 20)
    private String estado = "ACTIVO";

    // --- NUEVOS CAMPOS INSTITUCIONALES LEGALES ECUADOR ---
    @Column(length = 255)
    private String direccion;

    @Column(length = 15)
    private String telefono;

    @Column(length = 30)
    private String siglas;

    @Column(name = "segmento_seps", length = 20)
    private String segmentoSeps;

    @Column(name = "resolucion_seps", length = 100)
    private String resolucionSeps;

    @Column(name = "correo_institucional", length = 100)
    private String correoInstitucional;

    @Column(length = 100)
    private String provincia;

    @Column(length = 100)
    private String canton;

    // --- NUEVOS CAMPOS FINANCIEROS (REGLAS DE NEGOCIO) ---
    @Column(name = "saldo_minimo_apertura", precision = 15, scale = 2)
    private BigDecimal saldoMinimoApertura;

    @Column(name = "monto_minimo_credito", precision = 15, scale = 2)
    private BigDecimal montoMinimoCredito;

    @Column(name = "monto_maximo_credito", precision = 15, scale = 2)
    private BigDecimal montoMaximoCredito;

    @Column(name = "tasa_interes_anual", precision = 5, scale = 2)
    private BigDecimal tasaInteresAnual;

    @Column(name = "tasa_interes_mora", precision = 5, scale = 2)
    private BigDecimal tasaInteresMora;

    @Column(name = "costo_tramite", precision = 15, scale = 2)
    private BigDecimal costoTramite;

    @Column(name = "porcentaje_seguro_desgravamen", precision = 5, scale = 2)
    private BigDecimal porcentajeSeguroDesgravamen;

    @Column(name = "cuota_aportacion_mensual", precision = 15, scale = 2)
    private BigDecimal cuotaAportacionMensual;

    // --- NUEVOS ENLACES CONTABLES (RELACIONES JPA) ---
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_cartera_id")
    private PlanCuentas cuentaContableCartera;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_seguro_id")
    private PlanCuentas cuentaContableSeguro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_papeleria_id")
    private PlanCuentas cuentaContablePapeleria;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}