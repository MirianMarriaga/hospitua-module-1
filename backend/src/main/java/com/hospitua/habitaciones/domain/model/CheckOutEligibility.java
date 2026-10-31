package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import java.util.Objects;
import java.util.UUID;

/**
 * Estancia activa y habitación ya verificadas para el Check-Out (plan-registrar-check-out.md T009; FR-003, FR-004).
 * Las reglas no consultan nada: el Paso 1 las aplica con lo que leyó, y el registro del Check-Out puede repetirlas con
 * la habitación bloqueada.
 */
public record CheckOutEligibility(Stay stay, Room room) {

    public CheckOutEligibility {
        Objects.requireNonNull(stay, "La estancia no puede ser nula");
        Objects.requireNonNull(room, "La habitación no puede ser nula");
    }

    /**
     * Verifica en orden: que el par tenga una estancia activa (404 ACTIVE_STAY_NOT_FOUND), que la habitación exista
     * (404) y que esté {@code Occupied} (409 ROOM_INVALID_STATE con el estado actual).
     *
     * @param stay la estancia del par, o {@code null} si no hay una
     * @param room la habitación del inventario, o {@code null} si no existe
     */
    public static CheckOutEligibility verify(String reservationRef, UUID roomId, Stay stay, Room room) {
        if (stay == null || !stay.isActive()) {
            throw new ActiveStayNotFoundException(reservationRef, roomId);
        }
        if (room == null) {
            throw new RoomNotFoundException();
        }
        if (room.getStatus() != RoomStatus.Occupied) {
            throw new InvalidRoomTransitionException(
                    room.getStatus(),
                    "La habitación " + room.getRoomNumber() + " está "
                            + room.getStatus().displayName()
                            + "; solo se puede hacer el Check-Out de una habitación Ocupada.");
        }
        return new CheckOutEligibility(stay, room);
    }
}
