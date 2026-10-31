package com.hospitua.habitaciones.domain.exception;

/**
 * No hay una estancia activa con ese identificador (404 ACTIVE_STAY_NOT_FOUND; consultar-liquidacion Endpoint 2,
 * registrar-check-out T003).
 */
public final class ActiveStayNotFoundException extends DomainException {

    public ActiveStayNotFoundException() {
        super(DomainError.ACTIVE_STAY_NOT_FOUND, "No hay una estancia activa con ese identificador.");
    }
}
