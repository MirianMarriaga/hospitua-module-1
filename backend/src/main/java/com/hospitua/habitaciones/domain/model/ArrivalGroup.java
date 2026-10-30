package com.hospitua.habitaciones.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Reserva del día con habitaciones pendientes de check-in ({@code groups[]} de {@code GET /api/reception/arrivals}):
 * la interfaz la muestra como "RES-10482 · 1 de 2 pendientes" (plan-consultar-reservas.md T020, FR-005).
 */
public record ArrivalGroup(String reservationRef, int pendingRooms, int totalRooms, List<ArrivalItemSummary> rooms) {

    public ArrivalGroup {
        Objects.requireNonNull(reservationRef, "La referencia de la reserva no puede ser nula");
        rooms = List.copyOf(rooms);
    }
}
