package com.hospitua.habitaciones.domain.exception;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.Map;

/**
 * El estado actual de la habitación no admite la operación (409 ROOM_INVALID_STATE, con details.currentStatus).
 */
public final class InvalidRoomTransitionException extends DomainException {

    private final RoomStatus currentStatus;

    public InvalidRoomTransitionException(RoomStatus currentStatus) {
        this(currentStatus, "Transición de estado inválida para la habitación en estado: " + currentStatus);
    }

    public InvalidRoomTransitionException(RoomStatus currentStatus, String message) {
        super(DomainError.ROOM_INVALID_STATE, message, Map.of("currentStatus", currentStatus.name()));
        this.currentStatus = currentStatus;
    }

    public RoomStatus getCurrentStatus() {
        return currentStatus;
    }
}
