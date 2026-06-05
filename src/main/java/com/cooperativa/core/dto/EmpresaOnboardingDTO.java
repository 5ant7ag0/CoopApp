package com.cooperativa.core.dto;

import com.cooperativa.core.model.Empresa;
import com.cooperativa.core.model.UsuariosAdmin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO para el registro (onboarding) de nuevas cooperativas con su primer administrador.
 */
@Getter
@Setter
public class EmpresaOnboardingDTO {

    @NotNull(message = "Los datos de la empresa son obligatorios")
    @Valid
    private Empresa empresa;

    @NotNull(message = "Los datos del administrador inicial son obligatorios")
    @Valid
    private UsuariosAdmin admin;
}
