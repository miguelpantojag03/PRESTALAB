package com.prestalab.mapper;

import com.prestalab.domain.model.Usuario;
import com.prestalab.dto.UsuarioDTO;
import org.springframework.stereotype.Component;

@Component
public class UsuarioMapper {

    /** Nunca expone el passwordHash. */
    public UsuarioDTO toDto(Usuario usuario) {
        return new UsuarioDTO(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol(),
                usuario.isActivo());
    }
}
