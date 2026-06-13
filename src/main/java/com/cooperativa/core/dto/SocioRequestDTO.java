package com.cooperativa.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
public class SocioRequestDTO {

    @NotBlank(message = "El tipo de identificacion es obligatorio")
    private String tipoIdentificacion; // 'C' = Cédula, 'R' = RUC, 'P' = Pasaporte

    @NotBlank(message = "La identificacion es obligatoria")
    private String identificacion;

    @NotBlank(message = "Nombres completos son obligatorios")
    private String nombresCompletos;

    @NotBlank(message = "La direccion es obligatoria")
    private String direccion;

    @NotBlank(message = "El telefono es obligatorio")
    @Pattern(regexp = "^09\\d{8}$", message = "El telefono debe tener el formato de celular de Ecuador (09XXXXXXXX)")
    private String telefono;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo debe tener un formato valido")
    private String correo;

    @NotBlank(message = "La actividad economica es obligatoria")
    private String actividadEconomica;

    @NotNull(message = "Ingresos mensuales son obligatorios")
    @PositiveOrZero(message = "Ingresos mensuales deben ser cero o mas")
    private BigDecimal ingresosMensuales;

    @NotNull(message = "Gastos mensuales son obligatorios")
    @PositiveOrZero(message = "Gastos mensuales deben ser cero o mas")
    private BigDecimal gastosMensuales;

    @NotNull(message = "Deudas actuales son obligatorias")
    @PositiveOrZero(message = "Deudas actuales deben ser cero o mas")
    private BigDecimal deudasActuales;

    private String fotoPerfilUrl;
    private String fotoCedulaFrontalUrl;
    private String fotoCedulaPosteriorUrl;
    private Boolean esPep;
    private String estado;
}
