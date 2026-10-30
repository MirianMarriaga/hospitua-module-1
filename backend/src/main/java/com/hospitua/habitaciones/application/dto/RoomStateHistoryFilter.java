package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filtros combinables del historial de estados (consultar-historial FR-002). Un filtro nulo no restringe; el rango
 * {@code from}–{@code to} incluye los periodos que se cruzan con él, con días completos en la zona de Colombia.
 */
public record RoomStateHistoryFilter(
        UUID roomId, String roomNumber, RoomCategory categoryRoom, RoomStatus status, LocalDate from, LocalDate to) {

    public RoomStateHistoryFilter {
        roomNumber = roomNumber == null || roomNumber.isBlank() ? null : Room.normalizeRoomNumber(roomNumber);
    }

    /** Sin filtros: todo el historial. */
    public static RoomStateHistoryFilter none() {
        return new RoomStateHistoryFilter(null, null, null, null, null, null);
    }

    /** {@code true} si "Desde" es posterior a "Hasta" (FR-006). */
    public boolean fromAfterTo() {
        return from != null && to != null && from.isAfter(to);
    }
}
