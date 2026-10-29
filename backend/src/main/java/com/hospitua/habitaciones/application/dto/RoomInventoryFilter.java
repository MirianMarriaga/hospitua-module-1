package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.Objects;

/**
 * Filtros y orden del inventario de las vistas (consultar-inventario FR-003 a FR-005). Un filtro nulo no restringe;
 * sin {@code status} se incluyen todas, también las {@code Inactive}.
 */
public record RoomInventoryFilter(
        String roomNumber,
        RoomCategory categoryRoom,
        Integer floor,
        RoomStatus status,
        RoomInventorySort sort,
        boolean descending) {

    public RoomInventoryFilter {
        roomNumber = roomNumber == null || roomNumber.isBlank() ? null : Room.normalizeRoomNumber(roomNumber);
        sort = Objects.requireNonNullElse(sort, RoomInventorySort.ROOM_NUMBER);
    }

    /** Sin filtros, ordenado por número de forma ascendente. */
    public static RoomInventoryFilter none() {
        return new RoomInventoryFilter(null, null, null, null, RoomInventorySort.ROOM_NUMBER, false);
    }
}
