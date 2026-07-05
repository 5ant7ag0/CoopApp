package com.cooperativa.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "^[0-9]{10}001$", message = "El RUC debe tener 13 dígitos numéricos y terminar en 001")
    @Column(nullable = false, unique = true, length = 13)
    private String ruc;

    @NotBlank(message = "La razón social es obligatoria")
    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @NotBlank(message = "El nombre comercial es obligatorio")
    @Column(name = "nombre_comercial", nullable = false, length = 150)
    private String nombreComercial;

    @NotBlank(message = "El código SEPS es obligatorio")
    @Column(name = "codigo_seps", nullable = false, unique = true, length = 50)
    private String codigoSeps;

    @NotBlank(message = "El representante legal es obligatorio")
    @Column(name = "representante_legal", nullable = false, length = 100)
    private String representanteLegal;

    @NotBlank(message = "La cédula del representante es obligatoria")
    @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe tener 10 dígitos numéricos")
    @Column(name = "cedula_representante", nullable = false, length = 10)
    private String cedulaRepresentante;

    @Column(name = "logo_url", length = 255)
    private String logoUrl;

    @Column(length = 3)
    private String moneda = "USD";

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private TenantEstado estado = TenantEstado.ACTIVO;

    // --- NUEVOS CAMPOS DE LIMITES (SAAS) ---
    @Column(name = "limite_usuarios_admin")
    private Integer limiteUsuariosAdmin;

    @Column(name = "limite_socios")
    private Integer limiteSocios;

    // --- NUEVOS CAMPOS INSTITUCIONALES LEGALES ECUADOR ---
    @Column(length = 255)
    private String direccion;

    @Column(length = 15)
    private String telefono;

    @Column(length = 30)
    private String siglas;

    @NotBlank(message = "El segmento SEPS es obligatorio")
    @Column(name = "segmento_seps", length = 20)
    private String segmentoSeps;

    @Column(name = "resolucion_seps", length = 100)
    private String resolucionSeps;

    @NotBlank(message = "El correo institucional es obligatorio")
    @Email(message = "El formato del correo institucional es inválido")
    @Column(name = "correo_institucional", length = 100)
    private String correoInstitucional;

    @NotBlank(message = "El correo del representante es obligatorio")
    @Email(message = "El formato del correo del representante es inválido")
    @Column(name = "correo_gerente", length = 100)
    private String correoGerente;

    @Column(length = 100)
    private String provincia;

    @Column(length = 100)
    private String canton;

    // --- NUEVOS CAMPOS FINANCIEROS (REGLAS DE NEGOCIO) ---
    @Column(name = "saldo_minimo_apertura", precision = 15, scale = 2)
    private BigDecimal saldoMinimoApertura;

    @Deprecated
    @Column(name = "monto_minimo_credito", precision = 15, scale = 2)
    private BigDecimal montoMinimoCredito;

    @Deprecated
    @Column(name = "monto_maximo_credito", precision = 15, scale = 2)
    private BigDecimal montoMaximoCredito;

    @Deprecated
    @Column(name = "tasa_interes_anual", precision = 5, scale = 2)
    private BigDecimal tasaInteresAnual;

    @Deprecated
    @Column(name = "tasa_interes_mora", precision = 5, scale = 2)
    private BigDecimal tasaInteresMora;

    @Column(name = "costo_tramite", precision = 15, scale = 2)
    private BigDecimal costoTramite;

    @Deprecated
    @Column(name = "porcentaje_seguro_desgravamen", precision = 5, scale = 2)
    private BigDecimal porcentajeSeguroDesgravamen;

    @Column(name = "cuota_aportacion_mensual", precision = 15, scale = 2)
    private BigDecimal cuotaAportacionMensual;

    @Column(name = "tasa_interes_pasiva", precision = 5, scale = 2)
    private BigDecimal tasaInteresPasiva;

    @Column(name = "dias_gracia_mora")
    private Integer diasGraciaMora;

    // --- NUEVOS ENLACES CONTABLES (RELACIONES JPA) ---
    @Deprecated
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_cartera_id")
    private PlanCuentas cuentaContableCartera;

    @Deprecated
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_seguro_id")
    private PlanCuentas cuentaContableSeguro;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_papeleria_id")
    private PlanCuentas cuentaContablePapeleria;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_caja_id")
    private PlanCuentas cuentaContableCaja;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_obligaciones_id")
    private PlanCuentas cuentaContableObligaciones;

    @Deprecated
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_gastos_intereses_id")
    private PlanCuentas cuentaContableGastosIntereses;

    @Deprecated
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_ingresos_intereses_id")
    private PlanCuentas cuentaContableIngresosIntereses;

    @Deprecated
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_mora_id")
    private PlanCuentas cuentaContableMora;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cuenta_contable_aportaciones_id")
    private PlanCuentas cuentaContableAportaciones;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}