package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Fila de la pestaña Salidas: una estancia activa con salida hoy o vencida ({@code stays[]} de
 * {@code GET /api/reception/departures}, plan-consultar-panel-recepcion.md FR-004, FR-007). {@code guestCount} son
 * los registros {@code RoomGuest} de la estancia.
 */
public record DepartureSummary(
        UUID stayId,
        String reservationRef,
        UUID roomId,
        String roomNumber,
        String categoryRoom,
        String titularFirstName,
        String titularLastName,
        String titularDocumentNumber,
        LocalDate checkInDate,
        LocalDate expectedCheckoutDate,
        boolean overdue,
        int guestCount,
        String source) {

    public DepartureSummary {
        Objects.requireNonNull(stayId, "El id de la estancia no puede ser nulo");
        Objects.requireNonNull(expectedCheckoutDate, "La salida esperada no puede ser nula");
    }

    /** Fila de la estancia; es vencida si la salida esperada es anterior a {@code today} (etiqueta "Vencida", FR-007). */
    public static DepartureSummary of(
            Stay stay, String roomNumber, RoomCategory categoryRoom, int guestCount, LocalDate today) {
        return new DepartureSummary(
                stay.getId(),
                stay.getReservationRef(),
                stay.getRoomId(),
                roomNumber,
                categoryRoom.name(),
                stay.getTitularFirstName(),
                stay.getTitularLastName(),
                stay.getTitularDocumentNumber(),
                stay.getCheckInDate(),
                stay.getExpectedCheckoutDate(),
                stay.getExpectedCheckoutDate().isBefore(today),
                guestCount,
                stay.getSource());
    }
}
