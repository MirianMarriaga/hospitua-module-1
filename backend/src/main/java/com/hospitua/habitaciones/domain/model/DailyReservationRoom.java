package com.hospitua.habitaciones.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Habitación asignada a una reserva del día, tal como la envía Módulo 2. {@code categoryRoom} se guarda como texto:
 * {@code roomId} no tiene FK a {@code room}, porque una habitación desconocida se salta sin revertir la ingesta.
 */
public record DailyReservationRoom(UUID roomId, String roomNumber, String categoryRoom, int guestCount) {

    public DailyReservationRoom {
        Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        Objects.requireNonNull(roomNumber, "El número de habitación no puede ser nulo");
        Objects.requireNonNull(categoryRoom, "La categoría de habitación no puede ser nula");
    }
}
