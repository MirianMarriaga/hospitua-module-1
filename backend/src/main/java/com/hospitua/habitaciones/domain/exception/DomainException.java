package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * Excepción de una regla de negocio. Compone el {@link DomainError} del catálogo y los detalles que acompañan la
 * respuesta; cada excepción concreta solo elige su error y su mensaje.
 */
public class DomainException extends RuntimeException {

    private final DomainError error;
    private final Map<String, Object> details;

    protected DomainException(DomainError error, String message) {
        this(error, message, Map.of());
    }

    protected DomainException(DomainError error, String message, Map<String, Object> details) {
        super(message);
        this.error = error;
        this.details = Map.copyOf(details);
    }

    public DomainError error() {
        return error;
    }

    public Map<String, Object> details() {
        return details;
    }
}
