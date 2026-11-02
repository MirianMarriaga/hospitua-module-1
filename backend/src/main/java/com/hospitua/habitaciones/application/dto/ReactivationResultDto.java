package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/rooms/{roomId}/reactivation}. Con {@code alreadyReactivated} la baja ya estaba
 * revertida y no se hizo ningún cambio (FR-010, caso borde 2).
 */
public record ReactivationResultDto(
        UUID roomId,
        String roomNumber,
        int floor,
        RoomCategory categoryRoom,
        RoomStatus status,
        boolean alreadyReactivated) {

    public static ReactivationResultDto of(Room room, RoomStatus status, boolean alreadyReactivated) {
        return new ReactivationResultDto(
                room.getId(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getCategoryRoom(),
                status,
                alreadyReactivated);
    }
}
