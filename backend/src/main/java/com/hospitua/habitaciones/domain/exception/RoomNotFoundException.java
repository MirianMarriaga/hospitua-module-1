package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando una habitación solicitada no existe o no se encuentra disponible.
 */
public class RoomNotFoundException extends DomainException {

    private static final String DEFAULT_MESSAGE = "No se encontró la habitación.";

    public RoomNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public RoomNotFoundException(String message) {
        super(message);
    }
}
