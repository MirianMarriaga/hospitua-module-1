package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado de {@code POST /api/rooms/{roomId}/decommission} (FR-010): la habitación ya {@code Inactive} y su baja. El
 * Administrador queda registrado, pero no se muestra (FR-007).
 */
public record RoomDecommissionDto(
        UUID decommissionId,
        UUID roomId,
        String roomNumber,
        int floor,
        RoomCategory categoryRoom,
        RoomStatus status,
        DecommissionReason reasonCategory,
        String reasonDetail,
        OffsetDateTime decommissionDateTime) {

    public static RoomDecommissionDto of(Room room, RoomDecommission decommission) {
        return new RoomDecommissionDto(
                decommission.getId(),
                room.getId(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getCategoryRoom(),
                RoomStatus.Inactive,
                decommission.getReasonCategory(),
                decommission.getReasonDetail(),
                decommission.getDecommissionDateTime());
    }
}
