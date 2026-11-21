package com.prestalab.dto;

import com.prestalab.domain.enums.EstadoEquipo;
import jakarta.validation.constraints.NotNull;

/** Nuevo estado del equipo (RF02) */
public record CambioEstadoEquipoDTO(
        @NotNull(message = "El estado es obligatorio")
        EstadoEquipo estado) {
}
