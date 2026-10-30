package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * La cantidad de huéspedes difiere de la que confirmó Módulo 2 para la habitación (400 GUEST_COUNT_MISMATCH;
 * registrar-check-in FR-008, esc. 5).
 */
public final class GuestCountMismatchException extends DomainException {

    public GuestCountMismatchException(int expectedGuests, int receivedGuests) {
        super(
                DomainError.GUEST_COUNT_MISMATCH,
                "La reserva es para " + expectedGuests + " persona(s) en esta habitación y se registraron "
                        + receivedGuests + ".",
                Map.of("expectedGuests", expectedGuests, "receivedGuests", receivedGuests));
    }
}
