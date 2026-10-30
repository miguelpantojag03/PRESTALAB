package com.prestalab.dto;

import com.prestalab.domain.enums.EstadoEquipo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nuevo estado del equipo (RF02)")
public record CambioEstadoEquipoDTO(
        @Schema(example = "EN_MANTENIMIENTO")
        @NotNull(message = "El estado es obligatorio")
        EstadoEquipo estado) {
}
