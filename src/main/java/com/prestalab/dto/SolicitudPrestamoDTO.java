package com.prestalab.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Solicitud de préstamo (RF04); el estudiante sale del token, nunca del cuerpo")
public record SolicitudPrestamoDTO(
        @Schema(example = "1")
        @NotNull(message = "Debes elegir un equipo")
        Long equipoId,

        @Schema(example = "2026-11-03", description = "Entre hoy y hoy + 7 días (RN02)")
        @NotNull(message = "La fecha de devolución es obligatoria")
        @FutureOrPresent(message = "La fecha de devolución no puede estar en el pasado")
        LocalDate fechaDevolucionEsperada) {
}
