package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}