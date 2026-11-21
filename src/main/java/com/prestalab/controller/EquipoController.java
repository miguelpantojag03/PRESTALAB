package com.prestalab.controller;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.dto.CambioEstadoEquipoDTO;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import com.prestalab.service.EquipoService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/** RF02 y RF03. No existe DELETE: un equipo nunca se elimina, solo se desactiva (RN07). */
@RestController
@RequestMapping(path = "/api/v1/equipos", produces = MediaType.APPLICATION_JSON_VALUE)
public class EquipoController {

    private final EquipoService equipoService;

    public EquipoController(EquipoService equipoService) {
        this.equipoService = equipoService;
    }

    /** Consultar y filtrar el catálogo de equipos activos (RF03) */
    @GetMapping
    @PreAuthorize("hasAnyRole('ESTUDIANTE','ADMIN')")
    public ResponseEntity<List<EquipoDTO>> listarCatalogo(
            @RequestParam(required = false) CategoriaEquipo categoria,
            @RequestParam(defaultValue = "false") boolean soloDisponibles) {
        return ResponseEntity.ok(equipoService.listarCatalogo(categoria, soloDisponibles));
    }

    /** Consultar un equipo, activo o no (RF02) */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ESTUDIANTE','ADMIN')")
    public ResponseEntity<EquipoDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(equipoService.obtener(id));
    }

    /** Crear un equipo DISPONIBLE y activo (RF02) */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipoDTO> crear(@Valid @RequestBody EquipoFormDTO dto) {
        EquipoDTO creado = equipoService.crear(dto);
        return ResponseEntity.created(URI.create("/api/v1/equipos/" + creado.id())).body(creado);
    }

    /** Editar los datos descriptivos de un equipo (RF02) */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EquipoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody EquipoFormDTO dto) {
        return ResponseEntity.ok(equipoService.actualizar(id, dto));
    }

    /** Enviar a mantenimiento o volver a DISPONIBLE (RF02, RN06) */
    @PatchMapping(path = "/{id}/estado", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoEquipoDTO dto) {
        equipoService.cambiarEstado(id, dto.estado());
        return ResponseEntity.noContent().build();
    }

    /** Desactivar un equipo; nunca se elimina (RF02, RN07) */
    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        equipoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
