package com.hospitua.habitaciones.domain.exception;

/**
 * Ya existe una habitación con el número indicado, en cualquier estado o piso (409 ROOM_NUMBER_ALREADY_EXISTS). La
 * edición usa su propio texto, que aclara que cuenta también una habitación inactiva (plan-editar-habitacion.md).
 */
public final class RoomNumberAlreadyExistsException extends DomainException {

    public RoomNumberAlreadyExistsException(String roomNumber) {
        this(roomNumber, "");
    }

    private RoomNumberAlreadyExistsException(String roomNumber, String suffix) {
        super(
                DomainError.ROOM_NUMBER_ALREADY_EXISTS,
                "Ya existe una habitación con el número " + roomNumber + suffix + ".");
    }

    /** Al editar: "Ya existe una habitación con el número {número} (aunque esté inactiva)." */
    public static RoomNumberAlreadyExistsException whenEditing(String roomNumber) {
        return new RoomNumberAlreadyExistsException(roomNumber, " (aunque esté inactiva)");
    }
}
