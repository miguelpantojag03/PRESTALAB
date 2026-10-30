package com.prestalab.dto;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.domain.enums.EstadoEquipo;

public record EquipoDTO(Long id, String codigo, String nombre, String descripcion,
                        CategoriaEquipo categoria, EstadoEquipo estado, boolean activo) {
}
