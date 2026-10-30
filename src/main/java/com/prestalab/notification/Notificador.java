package com.prestalab.notification;

import com.prestalab.domain.model.Prestamo;

/** Puerto de notificación (D2): GestionPrestamos lo requiere; Notificaciones lo provee. */
public interface Notificador {

    void notificarCambioEstado(Prestamo prestamo);

    /** RF11: aviso de que el préstamo vence mañana. No implica un cambio de estado. */
    void notificarRecordatorio(Prestamo prestamo);
}
