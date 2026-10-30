package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de la pestaña Llegadas: una habitación pendiente de check-in de una reserva del día, con el estado actual de la
 * habitación ({@code groups[].rooms[]} de {@code GET /api/reception/arrivals}). {@code notice} es nulo sin aviso.
 */
public record ArrivalItemSummary(
        UUID roomId,
        String roomNumber,
        String categoryRoom,
        String titularFirstName,
        String titularLastName,
        String titularDocumentNumber,
        LocalDate startDate,
        LocalDate endDate,
        long nights,
        int guestCount,
        String source,
        RoomStatus roomStatus,
        ArrivalNotice notice) {

    public ArrivalItemSummary {
        Objects.requireNonNull(roomId, "El id de habitación no puede ser nulo");
        Objects.requireNonNull(roomStatus, "El estado de la habitación no puede ser nulo");
    }

    /** Fila de la habitación de la reserva, con las noches calculadas y el aviso del estado actual (FR-005). */
    public static ArrivalItemSummary of(DailyReservation reservation, DailyReservationRoom room, RoomStatus status) {
        return new ArrivalItemSummary(
                room.roomId(),
                room.roomNumber(),
                room.categoryRoom(),
                reservation.guestFirstName(),
                reservation.guestLastName(),
                reservation.guestDocumentNumber(),
                reservation.startDate(),
                reservation.endDate(),
                reservation.nights(),
                room.guestCount(),
                reservation.source(),
                status,
                ArrivalNotice.forStatus(status).orElse(null));
    }
}
