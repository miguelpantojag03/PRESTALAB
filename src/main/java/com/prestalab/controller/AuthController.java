package com.prestalab.controller;

import com.prestalab.dto.LoginDTO;
import com.prestalab.dto.RegistroUsuarioDTO;
import com.prestalab.dto.TokenDTO;
import com.prestalab.dto.UsuarioDTO;
import com.prestalab.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Registrarse como estudiante (RF01) */
    @PostMapping(path = "/registro", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UsuarioDTO> registrar(@Valid @RequestBody RegistroUsuarioDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrar(dto));
    }

    /** Iniciar sesión y obtener un token JWT (RF01) */
    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenDTO> iniciarSesion(@Valid @RequestBody LoginDTO dto) {
        return ResponseEntity.ok(usuarioService.iniciarSesion(dto));
    }
}
