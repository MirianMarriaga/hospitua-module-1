package com.hospitua.habitaciones.domain.exception;

/**
 * Módulo 2 no respondió, superó el timeout o rechazó la consulta de reservas (424 MODULE_2_UNAVAILABLE): la operación
 * se cancela sin cambiar la habitación (consultar-reservas T008, T026).
 */
public final class ExternalModule2UnavailableException extends DomainException {

    public ExternalModule2UnavailableException(Throwable cause) {
        super(DomainError.MODULE_2_UNAVAILABLE, "Servicio de reservas temporalmente no disponible.");
        initCause(cause);
    }
}
