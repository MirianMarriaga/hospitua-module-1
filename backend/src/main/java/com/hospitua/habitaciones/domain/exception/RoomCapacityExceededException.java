package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/** Los huéspedes superan la capacidad máxima de la habitación (400 ROOM_CAPACITY_EXCEEDED; registrar-check-in FR-008). */
public final class RoomCapacityExceededException extends DomainException {

    public RoomCapacityExceededException(int maxCapacity, int receivedGuests) {
        super(
                DomainError.ROOM_CAPACITY_EXCEEDED,
                "La habitación admite máximo " + maxCapacity + " persona(s) y se registraron " + receivedGuests + ".",
                Map.of("maxCapacity", maxCapacity, "receivedGuests", receivedGuests));
    }
}
