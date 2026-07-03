package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@Entity
@Table(name = "plan_cuentas")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PlanCuentas extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "codigo_contable", nullable = false, length = 30)
    private String codigoContable;

    @Column(name = "nombre_cuenta", nullable = false, length = 150)
    private String nombreCuenta;

    @Column(name = "tipo_cuenta", nullable = false, length = 20)
    private String tipoCuenta; // 'ACTIVO', 'PASIVO', 'PATRIMONIO', 'INGRESO', 'GASTO'

    @Column(name = "es_movimiento")
    private Boolean esMovimiento = true;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ACTIVO";
}