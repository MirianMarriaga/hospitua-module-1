package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import java.util.UUID;

/**
 * Habitación vendible para Módulo 2: solo los datos que necesita para asignar una reserva (consultar-inventario
 * FR-010). No expone estado, tarifa ni {@code version}.
 */
public record Module2RoomDto(UUID id, String roomNumber, RoomCategory categoryRoom, int maxCapacity) {

    public static Module2RoomDto from(Room room) {
        return new Module2RoomDto(room.getId(), room.getRoomNumber(), room.getCategoryRoom(), room.getMaxCapacity());
    }
}
