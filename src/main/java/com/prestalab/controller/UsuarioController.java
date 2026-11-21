package com.prestalab.controller;

import com.prestalab.dto.UsuarioDTO;
import com.prestalab.service.UsuarioService;
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
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    /** Listar los estudiantes por nombre (RF12) */
    @GetMapping("/estudiantes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UsuarioDTO>> listarEstudiantes() {
        return ResponseEntity.ok(usuarioService.listarEstudiantes());
    }

    /** Reactivar la cuenta de un estudiante (RF12) */
    @PostMapping("/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activar(@PathVariable Long id) {
        usuarioService.cambiarActivo(id, true);
        return ResponseEntity.noContent().build();
    }

    /** Desactivar la cuenta de un estudiante: no podrá iniciar sesión ni solicitar (RF12) */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        usuarioService.cambiarActivo(id, false);
        return ResponseEntity.noContent().build();
    }
}
