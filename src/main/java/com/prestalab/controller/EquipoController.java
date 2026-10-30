package com.prestalab.controller;

import com.prestalab.domain.enums.CategoriaEquipo;
import com.prestalab.dto.CambioEstadoEquipoDTO;
import com.prestalab.dto.EquipoDTO;
import com.prestalab.dto.EquipoFormDTO;
import com.prestalab.service.EquipoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Equipos", description = "Catálogo (RF03) y gestión de equipos (RF02)")
@SecurityRequirement(name = "bearerAuth")
public class EquipoController {

    private final EquipoService equipoService;

    public EquipoController(EquipoService equipoService) {
        this.equipoService = equipoService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ESTUDIANTE','ADMIN')")
    @Operation(summary = "Consultar y filtrar el catálogo de equipos activos (RF03)")
    @ApiResponse(responseCode = "200", description = "Equipos activos ordenados por nombre")
    @ApiResponse(responseCode = "400", description = "Categoría inválida")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    public ResponseEntity<List<EquipoDTO>> listarCatalogo(
            @RequestParam(required = false) CategoriaEquipo categoria,
            @RequestParam(defaultValue = "false") boolean soloDisponibles) {
        return ResponseEntity.ok(equipoService.listarCatalogo(categoria, soloDisponibles));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ESTUDIANTE','ADMIN')")
    @Operation(summary = "Consultar un equipo, activo o no (RF02)")
    @ApiResponse(responseCode = "200", description = "Equipo encontrado")
    @ApiResponse(responseCode = "400", description = "Id no numérico")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "404", description = "El equipo no existe")
    public ResponseEntity<EquipoDTO> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(equipoService.obtener(id));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear un equipo DISPONIBLE y activo (RF02)")
    @ApiResponse(responseCode = "201", description = "Equipo creado; la cabecera Location apunta a él")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o propiedades desconocidas")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "422", description = "Código de equipo duplicado")
    public ResponseEntity<EquipoDTO> crear(@Valid @RequestBody EquipoFormDTO dto) {
        EquipoDTO creado = equipoService.crear(dto);
        return ResponseEntity.created(URI.create("/api/v1/equipos/" + creado.id())).body(creado);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar los datos descriptivos de un equipo (RF02)")
    @ApiResponse(responseCode = "200", description = "Equipo actualizado; estado y activo no cambian")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El equipo no existe")
    @ApiResponse(responseCode = "422", description = "Código de equipo duplicado")
    public ResponseEntity<EquipoDTO> actualizar(@PathVariable Long id, @Valid @RequestBody EquipoFormDTO dto) {
        return ResponseEntity.ok(equipoService.actualizar(id, dto));
    }

    @PatchMapping(path = "/{id}/estado", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enviar a mantenimiento o volver a DISPONIBLE (RF02, RN06)")
    @ApiResponse(responseCode = "204", description = "Estado cambiado")
    @ApiResponse(responseCode = "400", description = "Estado inválido")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El equipo no existe")
    @ApiResponse(responseCode = "409", description = "Transición de estado del equipo no permitida")
    @ApiResponse(responseCode = "422", description = "PRESTADO no se asigna a mano")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoEquipoDTO dto) {
        equipoService.cambiarEstado(id, dto.estado());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/desactivar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar un equipo; nunca se elimina (RF02, RN07)")
    @ApiResponse(responseCode = "204", description = "Equipo desactivado")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El equipo no existe")
    @ApiResponse(responseCode = "409", description = "El equipo está PRESTADO")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        equipoService.desactivar(id);
        return ResponseEntity.noContent().build();
    }
}
