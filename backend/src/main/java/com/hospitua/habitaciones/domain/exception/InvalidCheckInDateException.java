package com.hospitua.habitaciones.domain.exception;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** La reserva no llega hoy en hora de Colombia (409 INVALID_CHECK_IN_DATE; registrar-check-in FR-005). */
public final class InvalidCheckInDateException extends DomainException {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public InvalidCheckInDateException(String reservationRef, LocalDate startDate) {
        super(
                DomainError.INVALID_CHECK_IN_DATE,
                "La reserva " + reservationRef + " llega el " + startDate.format(DISPLAY_DATE)
                        + "; el Check-In solo se puede hacer el día de llegada.");
    }
}
