package com.prestalab.domain.exception;

/**
 * Transición o guarda de D5 no cumplida; la lanzan solo las entidades.
 * El mensaje está en español y se puede mostrar al usuario.
 */
public class TransicionInvalidaException extends RuntimeException {

    public TransicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
