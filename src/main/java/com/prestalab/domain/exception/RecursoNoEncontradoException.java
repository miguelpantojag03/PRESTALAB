package com.prestalab.domain.exception;

/**
 * Un id solicitado no existe; la lanzan los servicios.
 * El mensaje está en español y se puede mostrar al usuario.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
