package com.cooperativa.core.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class DevengoManualRequestDTO {

    @NotNull(message = "La fecha de devengo es obligatoria")
    private LocalDate fecha;
}
