package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Entidad que representa las credenciales de acceso de los socios a la app movil.
 * Corresponde a la tabla 'socios_credenciales'.
 */
@Getter
@Setter
@Entity
@Table(name = "socios_credenciales")
public class SociosCredenciales extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id", nullable = false, unique = true)
    private Socio socio;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "token_mfa_secreto", length = 100)
    private String tokenMfaSecreto;

    @Column(name = "dispositivo_huella_id", length = 255)
    private String dispositivoHuellaId;

    @Column(name = "estado_acceso", length = 20)
    private String estadoAcceso = "ACTIVO";

    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;
}
