package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "transacciones_ledger", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"empresa_id", "referencia"})
})
public class TransaccionesLedger extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private CuentasAhorros cuenta;

    @Column(name = "tipo_transaccion", nullable = false, length = 10)
    private String tipoTransaccion; // 'DEBITO' o 'CREDITO'

    @Column(nullable = false)
    private BigDecimal monto;

    @Column(name = "saldo_anterior", nullable = false)
    private BigDecimal saldoAnterior;

    @Column(name = "saldo_resultante", nullable = false)
    private BigDecimal saldoResultante;

    @Column(nullable = false, length = 20)
    private String canal; // 'VENTANILLA', 'APP_MOVIL', etc.

    @Column(nullable = false, length = 100)
    private String referencia; // Hash o UUID único de la transacción financiera

    @Column(nullable = false, columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "usuario_admin_id")
    private Integer usuarioAdminId; // ID del cajero o funcionario si aplica

    @Column(name = "fecha_contable", updatable = false)
    private LocalDateTime fechaContable = LocalDateTime.now();

    //
    @Column(name = "direccion_ip", nullable = false, length = 45)
    private String direccionIp;

    @Column(name = "dispositivo_info", nullable = false, columnDefinition = "TEXT")
    private String dispositivoInfo;
}