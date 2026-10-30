package com.prestalab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Motivo del rechazo de una solicitud (RF05)")
public record RechazoPrestamoDTO(
        @Schema(example = "Equipo reservado para una clase")
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
        String motivo) {
}
