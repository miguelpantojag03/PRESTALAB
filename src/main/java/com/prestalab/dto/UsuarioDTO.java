package com.prestalab.dto;

import com.prestalab.domain.enums.Rol;

public record UsuarioDTO(Long id, String nombre, String correo, Rol rol, boolean activo) {
}
