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
@Table(name = "socios", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "identificacion"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Socio extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tipo_identificacion", nullable = false, length = 1)
    private String tipoIdentificacion; // 'C' = Cédula, 'R' = RUC, 'P' = Pasaporte

    @Column(nullable = false, length = 15)
    private String identificacion;

    @Column(name = "nombres_completos", nullable = false, length = 150)
    private String nombresCompletos;

    @Column(nullable = false, length = 255)
    private String direccion;

    @Column(nullable = false, length = 15)
    private String telefono; // Celular validado por regex de Ecuador (09XXXXXXXX)

    @Column(nullable = false, length = 100)
    private String correo;

    @Column(name = "actividad_economica", nullable = false, length = 100)
    private String actividadEconomica;

    @Column(name = "lugar_trabajo", length = 150)
    private String lugarTrabajo;

    // Precisión matemática usando BigDecimal para montos financieros
    @Column(name = "ingresos_mensuales", nullable = false)
    private BigDecimal ingresosMensuales = BigDecimal.ZERO;

    @Column(name = "gastos_mensuales", nullable = false)
    private BigDecimal gastosMensuales = BigDecimal.ZERO;

    @Column(name = "deudas_actuales", nullable = false)
    private BigDecimal deudasActuales = BigDecimal.ZERO;

    // Columna calculada en BD (Solo lectura en Java)
    @Column(name = "capacidad_pago", insertable = false, updatable = false)
    private BigDecimal capacidadPago;

    @Column(name = "foto_perfil_url", length = 255)
    private String fotoPerfilUrl;

    @Column(name = "foto_cedula_frontal_url", length = 255)
    private String fotoCedulaFrontalUrl;

    @Column(name = "foto_cedula_posterior_url", length = 255)
    private String fotoCedulaPosteriorUrl;

    @Column(name = "firma_url", length = 255)
    private String firmaUrl;

    @Column(name = "es_pep")
    private Boolean esPep = false;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private LocalDateTime phoneVerifiedAt;

    @Column(name = "estado_civil", length = 50)
    private String estadoCivil;

    @Column(length = 100)
    private String profesion;

    @PrePersist
    public void setDefaults() {
        if (this.getEstado() == null) {
            this.setEstado("PENDIENTE_APROBACION");
        }
    }

    @Transient
    private String estadoRiesgo = "SIN_CREDITO";
}