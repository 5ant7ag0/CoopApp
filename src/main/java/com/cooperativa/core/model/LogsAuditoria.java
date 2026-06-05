package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "logs_auditoria")
public class LogsAuditoria extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_admin_id")
    private Integer usuarioAdminId; // ID del empleado de la cooperativa (si aplica)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "socio_id")
    private Socio socio; // ID del socio (si la acción fue desde la App móvil)

    @Column(nullable = false, length = 100)
    private String accion; // Ej: 'AUTORIZAR_CREDITO', 'RETIRO_VENTANILLA'

    @Column(name = "tabla_afectada", nullable = false, length = 50)
    private String tablaAfectada;

    @Column(name = "registro_id", nullable = false)
    private Integer registroId;

    // Almacena dinámicamente los "Cambios realizados" en formato JSONB
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_anterior")
    private Map<String, Object> valorAnterior;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "valor_nuevo")
    private Map<String, Object> valorNuevo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(name = "direccion_ip", nullable = false, length = 45)
    private String direccionIp; // Guarda IPv4 o IPv6 desde el Request

    @Column(name = "dispositivo_info", nullable = false, columnDefinition = "TEXT")
    private String dispositivoInfo; // Almacena navegador o identificador único del móvil
}