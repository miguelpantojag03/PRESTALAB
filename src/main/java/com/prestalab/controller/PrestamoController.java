package com.prestalab.controller;

import com.prestalab.dto.PrestamoDTO;
import com.prestalab.dto.RechazoPrestamoDTO;
import com.prestalab.dto.SolicitudPrestamoDTO;
import com.prestalab.service.PrestamoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Entrada de D6. El estudiante siempre sale del "sub" del token: nunca se acepta un usuarioId
 * en el cuerpo ni en la URL.
 */
@RestController
@RequestMapping(path = "/api/v1/prestamos", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Préstamos", description = "Solicitud, aprobación, rechazo, devolución e historial (RF04 a RF07)")
@SecurityRequirement(name = "bearerAuth")
public class PrestamoController {

    private final PrestamoService prestamoService;

    public PrestamoController(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @Operation(summary = "Solicitar un préstamo (RF04, RN01 a RN06)")
    @ApiResponse(responseCode = "201", description = "Préstamo creado en PENDIENTE")
    @ApiResponse(responseCode = "400", description = "Datos inválidos o fecha pasada")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo los estudiantes solicitan")
    @ApiResponse(responseCode = "404", description = "El equipo no existe")
    @ApiResponse(responseCode = "422", description = "Regla de negocio incumplida o cuenta desactivada")
    public ResponseEntity<PrestamoDTO> solicitarPrestamo(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody SolicitudPrestamoDTO dto) {
        PrestamoDTO prestamo = prestamoService.solicitarPrestamo(Long.valueOf(jwt.getSubject()), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }

    @GetMapping("/mios")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    @Operation(summary = "Ver mi historial de préstamos (RF07)")
    @ApiResponse(responseCode = "200", description = "Préstamos del estudiante, del más reciente al más antiguo")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo los estudiantes")
    public ResponseEntity<List<PrestamoDTO>> listarPorUsuario(@Parameter(hidden = true)
                                                              @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(prestamoService.listarPorUsuario(Long.valueOf(jwt.getSubject())));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar las solicitudes PENDIENTES (RF05)")
    @ApiResponse(responseCode = "200", description = "Solicitudes por fecha de solicitud ascendente")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    public ResponseEntity<List<PrestamoDTO>> listarPendientes() {
        return ResponseEntity.ok(prestamoService.listarPendientes());
    }

    @GetMapping("/por-devolver")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar los préstamos APROBADOS y VENCIDOS (RF06)")
    @ApiResponse(responseCode = "200", description = "Préstamos por fecha de devolución esperada ascendente")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    public ResponseEntity<List<PrestamoDTO>> listarPorDevolver() {
        return ResponseEntity.ok(prestamoService.listarPorDevolver());
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Aprobar una solicitud PENDIENTE (RF05, RN05)")
    @ApiResponse(responseCode = "200", description = "Préstamo APROBADO; el equipo pasa a PRESTADO")
    @ApiResponse(responseCode = "400", description = "Id no numérico")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El préstamo no existe")
    @ApiResponse(responseCode = "409", description = "No está PENDIENTE o el equipo no está DISPONIBLE")
    @ApiResponse(responseCode = "422", description = "El equipo ya fue prestado en otra solicitud")
    public ResponseEntity<PrestamoDTO> aprobarPrestamo(@PathVariable Long id) {
        return ResponseEntity.ok(prestamoService.aprobarPrestamo(id));
    }

    @PostMapping(path = "/{id}/rechazar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rechazar una solicitud PENDIENTE con un motivo (RF05)")
    @ApiResponse(responseCode = "200", description = "Préstamo RECHAZADO")
    @ApiResponse(responseCode = "400", description = "Motivo vacío o demasiado largo")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El préstamo no existe")
    @ApiResponse(responseCode = "409", description = "El préstamo no está PENDIENTE")
    public ResponseEntity<PrestamoDTO> rechazarPrestamo(@PathVariable Long id,
                                                        @Valid @RequestBody RechazoPrestamoDTO dto) {
        return ResponseEntity.ok(prestamoService.rechazarPrestamo(id, dto.motivo()));
    }

    @PostMapping("/{id}/devolver")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar la devolución (RF06, RN05)")
    @ApiResponse(responseCode = "200", description = "Préstamo DEVUELTO; el equipo vuelve a DISPONIBLE")
    @ApiResponse(responseCode = "400", description = "Id no numérico")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "Solo el administrador")
    @ApiResponse(responseCode = "404", description = "El préstamo no existe")
    @ApiResponse(responseCode = "409", description = "El préstamo no está APROBADO ni VENCIDO")
    public ResponseEntity<PrestamoDTO> registrarDevolucion(@PathVariable Long id) {
        return ResponseEntity.ok(prestamoService.registrarDevolucion(id));
    }
}
