package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * Un rango de fechas incumple su regla (400 INVALID_DATE_RANGE, con {@code details.rule}). Cada regla tiene su
 * fábrica y su mensaje (plan-consultar-informacion-mantenimientos.md, tabla "Mensajes por regla").
 */
public final class InvalidDateRangeException extends DomainException {

    private final String rule;

    private InvalidDateRangeException(String rule, String message) {
        super(DomainError.INVALID_DATE_RANGE, message, Map.of("rule", rule));
        this.rule = rule;
    }

    /** La regla incumplida, la misma de {@code details.rule}. */
    public String rule() {
        return rule;
    }

    /** "Desde" posterior a "Hasta" en Consultar historial de estados (FR-006). */
    public static InvalidDateRangeException fromAfterTo() {
        return new InvalidDateRangeException("FROM_AFTER_TO", "La fecha \"Desde\" no puede ser posterior a \"Hasta\".");
    }

    /** Fin anterior al inicio: la consulta de mantenimientos y la programación del bloqueo técnico. */
    public static InvalidDateRangeException endBeforeStart() {
        return new InvalidDateRangeException(
                "END_BEFORE_START", "La fecha de fin no puede ser anterior a la fecha de inicio.");
    }

    /** El rango consultado termina antes de hoy (consultar-informacion-mantenimientos FR-006). */
    public static InvalidDateRangeException rangeInPast() {
        return new InvalidDateRangeException("RANGE_IN_PAST", "El rango consultado termina antes de hoy.");
    }

    /** Inicio del bloqueo técnico anterior a hoy (programar-bloqueo-tecnico FR-012). */
    public static InvalidDateRangeException startInPast() {
        return new InvalidDateRangeException("START_IN_PAST", "La fecha de inicio no puede ser anterior a hoy.");
    }

    /** El bloqueo técnico dura más que {@code maxRangeDays} (programar-bloqueo-tecnico FR-014). */
    public static InvalidDateRangeException rangeTooLong(int maxRangeDays) {
        return new InvalidDateRangeException(
                "RANGE_TOO_LONG", "El mantenimiento no puede durar más de " + maxRangeDays + " días.");
    }

    /** El bloqueo técnico inicia con más de {@code maxAdvanceDays} de antelación (programar-bloqueo-tecnico FR-014). */
    public static InvalidDateRangeException startTooFar(int maxAdvanceDays) {
        return new InvalidDateRangeException(
                "START_TOO_FAR", "Solo se puede programar con hasta " + maxAdvanceDays + " días de anticipación.");
    }
}
