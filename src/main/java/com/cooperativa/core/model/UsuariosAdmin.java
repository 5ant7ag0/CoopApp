package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "usuarios_admin", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "username"})
})
public class UsuariosAdmin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    @Column(name = "nombres_completos", nullable = false, length = 150)
    private String nombresCompletos;

    @Column(nullable = false, length = 100)
    private String correo;

    @Column(nullable = false, length = 50)
    private String rol; // 'CAJERO', 'OFICIAL_DE_CREDITO', 'GERENTE', 'AUDITOR'

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO"; // 'ACTIVO' o 'INACTIVO'

    @Column(nullable = false, unique = true, length = 20)
    private String identificacion;

    @Column(name = "foto_perfil_url", columnDefinition = "TEXT")
    private String fotoPerfilUrl;

    @Column(length = 20)
    private String telefono;

    @Column(length = 255)
    private String direccion;

    @Column(name = "cambiar_password_proximo_inicio", nullable = false)
    private boolean cambiarPasswordProximoInicio = true;

    @Column(name = "caja_id")
    private Integer cajaId;

    @Column(name = "limite_transaccion_max", nullable = false)
    private java.math.BigDecimal limiteTransaccionMax = java.math.BigDecimal.ZERO;

    @Column(name = "ultimo_acceso")
    private java.time.LocalDateTime ultimoAcceso;
}