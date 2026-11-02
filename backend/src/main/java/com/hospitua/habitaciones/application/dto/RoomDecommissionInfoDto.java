package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/rooms/{roomId}/decommission}: la habitación {@code Inactive} y su baja vigente, para la
 * confirmación de la reactivación (FR-009).
 */
public record RoomDecommissionInfoDto(
        UUID roomId,
        String roomNumber,
        int floor,
        RoomCategory categoryRoom,
        RoomStatus status,
        UUID decommissionId,
        OffsetDateTime decommissionDateTime,
        DecommissionReason reasonCategory,
        String reasonDetail) {

    public static RoomDecommissionInfoDto of(Room room, RoomDecommission decommission) {
        return new RoomDecommissionInfoDto(
                room.getId(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getCategoryRoom(),
                room.getStatus(),
                decommission.getId(),
                decommission.getDecommissionDateTime(),
                decommission.getReasonCategory(),
                decommission.getReasonDetail());
    }
}
