package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * Un atributo de la habitación no cumple su regla (400 VALIDATION_ERROR, con {@code details.field}). Respalda en el
 * dominio las mismas reglas que valida el DTO de la petición.
 */
public final class InvalidRoomDataException extends DomainException {

    public InvalidRoomDataException(String field, String message) {
        super(DomainError.VALIDATION_ERROR, message, Map.of("field", field));
    }
}
