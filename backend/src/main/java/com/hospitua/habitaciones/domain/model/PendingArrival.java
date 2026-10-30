package com.hospitua.habitaciones.domain.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reserva del día con todas sus habitaciones y los ids de las que todavía no tienen una {@code Stay} para esa reserva
 * (llegadas pendientes, el mismo criterio que {@code findPendingArrivalReservationRef}).
 */
public record PendingArrival(DailyReservation reservation, Set<UUID> pendingRoomIds) {

    public PendingArrival {
        Objects.requireNonNull(reservation, "La reserva no puede ser nula");
        pendingRoomIds = Set.copyOf(pendingRoomIds);
    }

    public boolean isPending(UUID roomId) {
        return pendingRoomIds.contains(roomId);
    }
}
