package com.prestalab.service;

import com.prestalab.dto.PrestamoDTO;
import com.prestalab.dto.SolicitudPrestamoDTO;

import java.util.List;

public interface PrestamoService {

    PrestamoDTO solicitarPrestamo(Long usuarioId, SolicitudPrestamoDTO dto);

    PrestamoDTO aprobarPrestamo(Long prestamoId);

    PrestamoDTO rechazarPrestamo(Long prestamoId, String motivo);

    PrestamoDTO registrarDevolucion(Long prestamoId);

    List<PrestamoDTO> listarPorUsuario(Long usuarioId);

    List<PrestamoDTO> listarPendientes();

    /** Préstamos APROBADOS y VENCIDOS (los que se pueden devolver, RF06), por fecha de devolución esperada. */
    List<PrestamoDTO> listarPorDevolver();

    int marcarPrestamosVencidos();

    int enviarRecordatorios();
}
