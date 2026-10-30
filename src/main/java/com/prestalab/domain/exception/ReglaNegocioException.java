package com.prestalab.domain.exception;

/**
 * Regla de negocio incumplida; la lanzan solo los servicios.
 * El mensaje está en español y se puede mostrar al usuario.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
