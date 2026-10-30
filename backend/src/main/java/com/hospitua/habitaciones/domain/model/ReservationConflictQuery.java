package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Consulta a Módulo 2 de las reservas de una sola habitación en un rango de fechas inclusivo (consultar-reservas
 * FR-023, T006). El invocador ya validó el rango con sus reglas de negocio; un rango invertido aquí es un error de
 * programación.
 */
public record ReservationConflictQuery(UUID roomId, LocalDate startDate, LocalDate endDate) {

    public ReservationConflictQuery {
        Objects.requireNonNull(roomId, "El id de la habitación no puede ser nulo");
        Objects.requireNonNull(startDate, "La fecha de inicio no puede ser nula");
        Objects.requireNonNull(endDate, "La fecha de fin no puede ser nula");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la de inicio");
        }
    }
}
