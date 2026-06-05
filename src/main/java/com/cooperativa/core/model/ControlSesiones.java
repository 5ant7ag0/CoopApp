package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "control_sesiones")
public class ControlSesiones extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "usuario_admin_id")
    private Integer usuarioAdminId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id")
    private Socio socio;

    @Column(name = "token_jwt_hash", nullable = false, unique = true, length = 64)
    private String tokenJwtHash; // Hash SHA-256 del JWT para búsquedas rápidas indexadas

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio = LocalDateTime.now();

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "ultima_actividad", nullable = false)
    private LocalDateTime ultimaActividad = LocalDateTime.now();

    @Column(name = "direccion_ip", nullable = false, length = 45)
    private String direccionIp;

    @Column(name = "dispositivo_info", nullable = false, columnDefinition = "TEXT")
    private String dispositivoInfo;

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVA"; // 'ACTIVA', 'CERRADA', 'EXPIRADA', 'REVOCADA'
}