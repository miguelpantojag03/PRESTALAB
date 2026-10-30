package com.prestalab.dto;

import com.prestalab.domain.enums.EstadoPrestamo;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PrestamoDTO(Long id, Long usuarioId, String nombreUsuario, String correoUsuario,
                          Long equipoId, String codigoEquipo, String nombreEquipo,
                          LocalDate fechaSolicitud, LocalDate fechaDevolucionEsperada,
                          LocalDateTime fechaAprobacion, LocalDate fechaDevolucionReal,
                          EstadoPrestamo estado, String motivoRechazo) {
}
