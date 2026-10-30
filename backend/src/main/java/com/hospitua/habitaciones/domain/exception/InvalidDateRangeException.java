package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * Un rango de fechas incumple su regla (400 INVALID_DATE_RANGE, con {@code details.rule}). Cada regla tiene su
 * fábrica y su mensaje (plan-consultar-informacion-mantenimientos.md, tabla "Mensajes por regla").
 */
public final class InvalidDateRangeException extends DomainException {

    private InvalidDateRangeException(String rule, String message) {
        super(DomainError.INVALID_DATE_RANGE, message, Map.of("rule", rule));
    }

    /** "Desde" posterior a "Hasta" en Consultar historial de estados (FR-006). */
    public static InvalidDateRangeException fromAfterTo() {
        return new InvalidDateRangeException("FROM_AFTER_TO", "La fecha \"Desde\" no puede ser posterior a \"Hasta\".");
    }
}
