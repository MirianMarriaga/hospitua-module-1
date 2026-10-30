package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Datos de una habitación con su {@code version} para el bloqueo optimista. Lo devuelven el registro, el detalle
 * del inventario y la edición.
 */
public record RoomDto(
        UUID roomId,
        RoomStatus status,
        String roomNumber,
        int floor,
        RoomCategory categoryRoom,
        int maxCapacity,
        BigDecimal baseRate,
        int version) {

    public static RoomDto from(Room room) {
        return new RoomDto(
                room.getId(),
                room.getStatus(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getCategoryRoom(),
                room.getMaxCapacity(),
                room.getBaseRate(),
                room.getVersion());
    }
}
