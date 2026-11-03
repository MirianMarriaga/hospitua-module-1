package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Días evaluados frente a los mantenimientos, inclusivos en los dos extremos
 * (plan-consultar-informacion-mantenimientos.md T001, T002). Solo se crea con sus fábricas, que reciben {@code today}
 * (de {@code Clock} en el servicio) y aplican las reglas de cada consumidor antes de cualquier consulta (FR-009).
 */
public record MaintenanceDateRange(LocalDate firstDay, LocalDate lastDay) {

    public MaintenanceDateRange {
        Objects.requireNonNull(firstDay, "El primer día no puede ser nulo");
        Objects.requireNonNull(lastDay, "El último día no puede ser nulo");
        if (lastDay.isBefore(firstDay)) {
            throw new IllegalArgumentException("El último día no puede ser anterior al primero");
        }
    }

    /**
     * Consulta de disponibilidad (Módulo 2 y bloqueo técnico): sin límites de duración ni de antelación; un rango que
     * empieza antes de hoy se evalúa desde hoy (FR-005 a FR-008).
     *
     * @throws InvalidDateRangeException {@code END_BEFORE_START} si el último día es anterior al primero, o
     *     {@code RANGE_IN_PAST} si el rango termina antes de hoy
     */
    public static MaintenanceDateRange forAvailability(LocalDate today, LocalDate firstDay, LocalDate lastDay) {
        if (lastDay.isBefore(firstDay)) {
            throw InvalidDateRangeException.endBeforeStart();
        }
        if (lastDay.isBefore(today)) {
            throw InvalidDateRangeException.rangeInPast();
        }
        return new MaintenanceDateRange(firstDay.isBefore(today) ? today : firstDay, lastDay);
    }

    /**
     * Programación de un bloqueo técnico, con el fin estimado incluido (programar-bloqueo-tecnico FR-012 a FR-014). La
     * duración es {@code fin − inicio} y la antelación {@code inicio − hoy}, en días; un solo día (fin = inicio) es
     * válido.
     *
     * @throws InvalidDateRangeException con la primera regla incumplida, en este orden: {@code END_BEFORE_START},
     *     {@code START_IN_PAST}, {@code RANGE_TOO_LONG} y {@code START_TOO_FAR}
     */
    public static MaintenanceDateRange forScheduling(
            LocalDate today, MaintenanceLimits limits, LocalDate startDate, LocalDate estimatedEndDate) {
        if (estimatedEndDate.isBefore(startDate)) {
            throw InvalidDateRangeException.endBeforeStart();
        }
        if (startDate.isBefore(today)) {
            throw InvalidDateRangeException.startInPast();
        }
        if (ChronoUnit.DAYS.between(startDate, estimatedEndDate) > limits.maxRangeDays()) {
            throw InvalidDateRangeException.rangeTooLong(limits.maxRangeDays());
        }
        if (ChronoUnit.DAYS.between(today, startDate) > limits.maxAdvanceDays()) {
            throw InvalidDateRangeException.startTooFar(limits.maxAdvanceDays());
        }
        return new MaintenanceDateRange(startDate, estimatedEndDate);
    }

    /** El día está entre el primero y el último, incluidos. */
    public boolean includes(LocalDate day) {
        return !day.isBefore(firstDay) && !day.isAfter(lastDay);
    }
}
