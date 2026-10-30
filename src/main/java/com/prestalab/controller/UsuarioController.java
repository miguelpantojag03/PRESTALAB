package com.prestalab.controller;

import com.prestalab.dto.UsuarioDTO;
import com.prestalab.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Estudiantes", description = "Gestión de estudiantes (RF12)")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/estudiantes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar los estudiantes por nombre (RF12)")
    @ApiResponse(responseCode = "200", description = "Estudiantes, activos e inactivos")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    public ResponseEntity<List<UsuarioDTO>> listarEstudiantes() {
        return ResponseEntity.ok(usuarioService.listarEstudiantes());
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reactivar la cuenta de un estudiante (RF12)")
    @ApiResponse(responseCode = "204", description = "Cuenta activa")
    @ApiResponse(responseCode = "400", description = "Id no numérico")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El usuario no existe")
    @ApiResponse(responseCode = "422", description = "Es una cuenta de administrador")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        usuarioService.cambiarActivo(id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar la cuenta de un estudiante: no podrá iniciar sesión ni solicitar (RF12)")
    @ApiResponse(responseCode = "204", description = "Cuenta desactivada")
    @ApiResponse(responseCode = "400", description = "Id no numérico")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El usuario no existe")
    @ApiResponse(responseCode = "422", description = "Es una cuenta de administrador")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.cambiarActivo(id, false);
        return ResponseEntity.noContent().build();
    }
}
