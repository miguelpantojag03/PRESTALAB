package com.prestalab.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Solicitud de préstamo (RF04); el estudiante sale del token, nunca del cuerpo */
public record SolicitudPrestamoDTO(
        @NotNull(message = "Debes elegir un equipo")
        Long equipoId,

        // Entre hoy y hoy + 7 días (RN02)
        @NotNull(message = "La fecha de devolución es obligatoria")
        @FutureOrPresent(message = "La fecha de devolución no puede estar en el pasado")
        LocalDate fechaDevolucionEsperada) {
}
