package com.hospitua.habitaciones.domain.exception;

import java.util.Map;
import java.util.UUID;

/**
 * No hay una estancia activa con ese identificador o para ese par reserva y habitación (404 ACTIVE_STAY_NOT_FOUND;
 * consultar-liquidacion Endpoint 2, registrar-check-out T003 y Endpoint 1).
 */
public final class ActiveStayNotFoundException extends DomainException {

    public ActiveStayNotFoundException() {
        super(DomainError.ACTIVE_STAY_NOT_FOUND, "No hay una estancia activa con ese identificador.");
    }

    /** Paso 1 del Check-Out: la estancia se busca por el par, que viaja en {@code details}. */
    public ActiveStayNotFoundException(String reservationRef, UUID roomId) {
        super(
                DomainError.ACTIVE_STAY_NOT_FOUND,
                "No hay una estancia activa de la reserva " + reservationRef + " en esta habitación.",
                Map.of("reservationRef", reservationRef, "roomId", roomId.toString()));
    }
}
