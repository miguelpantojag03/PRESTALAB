package com.prestalab.service;

import com.prestalab.dto.LoginDTO;
import com.prestalab.dto.RegistroUsuarioDTO;
import com.prestalab.dto.TokenDTO;
import com.prestalab.dto.UsuarioDTO;

import java.util.List;

public interface UsuarioService {

    UsuarioDTO registrar(RegistroUsuarioDTO dto);

    TokenDTO iniciarSesion(LoginDTO dto);

    List<UsuarioDTO> listarEstudiantes();

    void cambiarActivo(Long id, boolean activo);
}
