package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * Un dato de un huésped del Check-In no cumple su regla (400 VALIDATION_ERROR, con {@code details.field} y
 * {@code details.guestIndex}; registrar-check-in FR-006, FR-007, FR-009).
 */
public final class InvalidGuestDataException extends DomainException {

    public InvalidGuestDataException(int guestIndex, String field, String message) {
        super(DomainError.VALIDATION_ERROR, message, Map.of("field", field, "guestIndex", guestIndex));
    }
}
