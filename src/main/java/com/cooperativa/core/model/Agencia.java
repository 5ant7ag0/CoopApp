package com.cooperativa.core.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "agencias",
    uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "codigo"})
)
public class Agencia extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 255)
    private String direccion;

    @PrePersist
    public void setDefaults() {
        if (this.getEstado() == null) {
            this.setEstado("ACTIVA");
        }
    }
}
