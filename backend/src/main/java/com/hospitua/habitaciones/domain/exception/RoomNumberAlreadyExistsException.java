package com.hospitua.habitaciones.domain.exception;

/**
 * Ya existe una habitación con el número indicado, en cualquier estado o piso (409 ROOM_NUMBER_ALREADY_EXISTS).
 */
public final class RoomNumberAlreadyExistsException extends DomainException {

    public RoomNumberAlreadyExistsException(String roomNumber) {
        super(DomainError.ROOM_NUMBER_ALREADY_EXISTS, "Ya existe una habitación con el número " + roomNumber + ".");
    }
}
