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
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String passwordHash;

    @Column(name = "nombres_completos", nullable = false, length = 150)
    private String nombresCompletos;

    @Column(nullable = false, length = 100)
    private String correo;

    @Column(nullable = false, length = 50)
    private String rol; // 'CAJERO', 'OFICIAL_DE_CREDITO', 'GERENTE', 'AUDITOR'

    @Column(nullable = false, length = 20)
    private String estado = "ACTIVO"; // 'ACTIVO' o 'INACTIVO'
}