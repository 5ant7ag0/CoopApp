package com.cooperativa.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantUpdateGeneralDTO {

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "^[0-9]{13}$", message = "El RUC debe tener 13 dígitos numéricos")
    private String ruc;

    @NotBlank(message = "La razón social es obligatoria")
    private String razonSocial;

    @NotBlank(message = "El nombre comercial es obligatorio")
    private String nombreComercial;

    @NotBlank(message = "El representante legal es obligatorio")
    private String representanteLegal;

    @NotBlank(message = "La cédula del representante es obligatoria")
    @Pattern(regexp = "^[0-9]{10}$", message = "La cédula debe tener 10 dígitos numéricos")
    private String cedulaRepresentante;

    @NotBlank(message = "El código SEPS es obligatorio")
    private String codigoSeps;

    private String segmentoSeps;

    @NotBlank(message = "El correo institucional es obligatorio")
    @Email(message = "El formato del correo institucional es inválido")
    private String correoInstitucional;

    private String direccion;
    private String telefono;
}
