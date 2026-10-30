package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Reservas que Módulo 2 devolvió para la habitación y el rango consultados (consultar-reservas T006). Cada una es un
 * conflicto; sin reservas, la operación es segura. No se reevalúan estados ni fechas.
 */
public record ReservationConflictResult(List<ConflictingReservation> reservations) {

    public ReservationConflictResult {
        reservations = List.copyOf(reservations);
    }

    public boolean hasConflict() {
        return !reservations.isEmpty();
    }

    /** Reserva vigente de Módulo 2 que se cruza con el rango; {@code status} es PENDING, ACTIVE o IN_PROGRESS. */
    public record ConflictingReservation(String reservationRef, String status, LocalDate startDate, LocalDate endDate) {

        public ConflictingReservation {
            Objects.requireNonNull(reservationRef, "reservationRef");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(startDate, "startDate");
            Objects.requireNonNull(endDate, "endDate");
        }
    }
}
