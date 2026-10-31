package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * La descripción del daño falta, es solo espacios o pasa de 500 caracteres (400 VALIDATION_ERROR, con
 * {@code details.field}; inhabilitada-por-reparaciones FR-003, caso límite 2).
 */
public final class InvalidDamageDescriptionException extends DomainException {

    public static final String MESSAGE = "La descripción del daño es obligatoria (máximo 500 caracteres).";

    public InvalidDamageDescriptionException() {
        super(DomainError.VALIDATION_ERROR, MESSAGE, Map.of("field", "damageDescription"));
    }
}
