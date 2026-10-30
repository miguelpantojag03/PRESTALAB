package com.prestalab.controller;

import com.prestalab.dto.LoginDTO;
import com.prestalab.dto.RegistroUsuarioDTO;
import com.prestalab.dto.TokenDTO;
import com.prestalab.dto.UsuarioDTO;
import com.prestalab.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Autenticación", description = "Registro e inicio de sesión (RF01)")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping(path = "/registro", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Registrarse como estudiante (RF01)")
    @ApiResponse(responseCode = "201", description = "Cuenta creada con rol ESTUDIANTE")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o propiedades desconocidas")
    @ApiResponse(responseCode = "422", description = "Ya existe una cuenta con ese correo")
    public ResponseEntity<UsuarioDTO> registrar(@Valid @RequestBody RegistroUsuarioDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.registrar(dto));
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Iniciar sesión y obtener un token JWT (RF01)")
    @ApiResponse(responseCode = "200", description = "Token JWT válido por 60 minutos")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos, o cuenta desactivada")
    public ResponseEntity<TokenDTO> iniciarSesion(@Valid @RequestBody LoginDTO dto) {
        return ResponseEntity.ok(usuarioService.iniciarSesion(dto));
    }
}
