package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción base para los errores de reglas de negocio del dominio.
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
