package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * Dos huéspedes de la habitación tienen el mismo tipo y número de documento (400 DUPLICATE_GUEST_DOCUMENT;
 * registrar-check-in T022). {@code details.guestIndex} señala el segundo huésped repetido.
 */
public final class DuplicateGuestDocumentException extends DomainException {

    public DuplicateGuestDocumentException(String documentType, String documentNumber, int guestIndex) {
        super(
                DomainError.DUPLICATE_GUEST_DOCUMENT,
                "Dos huéspedes tienen el mismo documento " + documentType + " " + documentNumber + ".",
                Map.of("guestIndex", guestIndex));
    }
}
