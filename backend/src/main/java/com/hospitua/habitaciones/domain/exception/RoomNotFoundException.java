package com.hospitua.habitaciones.domain.exception;

/**
 * La habitación solicitada no existe (404 ROOM_NOT_FOUND).
 */
public final class RoomNotFoundException extends DomainException {

    public RoomNotFoundException() {
        this("No se encontró la habitación.");
    }

    public RoomNotFoundException(String message) {
        super(DomainError.ROOM_NOT_FOUND, message);
    }
}
