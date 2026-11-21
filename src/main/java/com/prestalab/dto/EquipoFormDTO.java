package com.prestalab.dto;

import com.prestalab.domain.enums.CategoriaEquipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos descriptivos de un equipo (RF02); el estado y la actividad no se editan aquí */
public record EquipoFormDTO(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código no puede superar 30 caracteres")
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "El código solo admite letras mayúsculas, números y guiones")
        String codigo,
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,
        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String descripcion,
        @NotNull(message = "La categoría es obligatoria")
        CategoriaEquipo categoria) {
}
