package com.hospitua.habitaciones.domain.exception;

/**
 * Ya hay una estancia para el par reserva y habitación (409 STAY_ALREADY_EXISTS; registrar-check-in FR-003, esc. 10).
 */
public final class StayAlreadyExistsException extends DomainException {

    public StayAlreadyExistsException(String roomNumber, String reservationRef) {
        super(
                DomainError.STAY_ALREADY_EXISTS,
                "La habitación " + roomNumber + " ya tiene el Check-In registrado para la reserva " + reservationRef
                        + ".");
    }
}
