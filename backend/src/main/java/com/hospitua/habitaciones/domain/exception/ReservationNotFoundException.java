package com.hospitua.habitaciones.domain.exception;

/**
 * La reserva, o la habitación dentro de ella, no figura en la copia local del día (404 RESERVATION_NOT_FOUND;
 * plan-consultar-reservas.md T008, registrar-check-in esc. 7).
 */
public final class ReservationNotFoundException extends DomainException {

    public ReservationNotFoundException(String reservationRef) {
        super(
                DomainError.RESERVATION_NOT_FOUND,
                "La reserva " + reservationRef + " no figura en la lista de llegadas del día para esta habitación.");
    }
}
