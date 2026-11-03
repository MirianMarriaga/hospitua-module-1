package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * La justificación del bloqueo técnico falta, es solo espacios o pasa de 500 caracteres (400 VALIDATION_ERROR, con
 * {@code details.field = reason}; programar-bloqueo-tecnico FR-005, caso límite 3).
 */
public final class InvalidTechnicalBlockReasonException extends DomainException {

    public static final String MESSAGE = "Escribe el motivo del mantenimiento (máximo 500 caracteres).";

    public InvalidTechnicalBlockReasonException() {
        super(DomainError.VALIDATION_ERROR, MESSAGE, Map.of("field", "reason"));
    }
}
