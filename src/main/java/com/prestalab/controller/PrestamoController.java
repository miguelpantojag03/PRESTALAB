package com.prestalab.controller;

import com.prestalab.dto.PrestamoDTO;
import com.prestalab.dto.RechazoPrestamoDTO;
import com.prestalab.dto.SolicitudPrestamoDTO;
import com.prestalab.service.PrestamoService;
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
public class PrestamoController {

    private final PrestamoService prestamoService;

    public PrestamoController(PrestamoService prestamoService) {
        this.prestamoService = prestamoService;
    }

    /** Solicitar un préstamo (RF04, RN01 a RN06) */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<PrestamoDTO> solicitarPrestamo(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody SolicitudPrestamoDTO dto) {
        PrestamoDTO prestamo = prestamoService.solicitarPrestamo(Long.valueOf(jwt.getSubject()), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }

    /** Ver mi historial de préstamos (RF07) */
    @GetMapping("/mios")
    @PreAuthorize("hasRole('ESTUDIANTE')")
    public ResponseEntity<List<PrestamoDTO>> listarPorUsuario(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(prestamoService.listarPorUsuario(Long.valueOf(jwt.getSubject())));
    }

    /** Listar las solicitudes PENDIENTES (RF05) */
    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PrestamoDTO>> listarPendientes() {
        return ResponseEntity.ok(prestamoService.listarPendientes());
    }

    /** Listar los préstamos APROBADOS y VENCIDOS (RF06) */
    @GetMapping("/por-devolver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PrestamoDTO>> listarPorDevolver() {
        return ResponseEntity.ok(prestamoService.listarPorDevolver());
    }

    /** Aprobar una solicitud PENDIENTE (RF05, RN05) */
    @PostMapping("/{id}/aprobar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PrestamoDTO> aprobarPrestamo(@PathVariable Long id) {
        return ResponseEntity.ok(prestamoService.aprobarPrestamo(id));
    }

    /** Rechazar una solicitud PENDIENTE con un motivo (RF05) */
    @PostMapping(path = "/{id}/rechazar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PrestamoDTO> rechazarPrestamo(@PathVariable Long id,
                                                        @Valid @RequestBody RechazoPrestamoDTO dto) {
        return ResponseEntity.ok(prestamoService.rechazarPrestamo(id, dto.motivo()));
    }

    /** Registrar la devolución (RF06, RN05) */
    @PostMapping("/{id}/devolver")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PrestamoDTO> registrarDevolucion(@PathVariable Long id) {
        return ResponseEntity.ok(prestamoService.registrarDevolucion(id));
    }
}
