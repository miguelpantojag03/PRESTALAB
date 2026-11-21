package com.prestalab.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo del rechazo de una solicitud (RF05) */
public record RechazoPrestamoDTO(
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar 500 caracteres")
        String motivo) {
}
