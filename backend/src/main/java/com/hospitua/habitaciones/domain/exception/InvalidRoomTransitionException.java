package com.hospitua.habitaciones.domain.exception;

import com.hospitua.habitaciones.domain.model.RoomStatus;

/**
 * Excepción lanzada al intentar una transición no permitida según la máquina de estados de Room.
 */
public class InvalidRoomTransitionException extends DomainException {

    private final RoomStatus currentStatus;

    public InvalidRoomTransitionException(RoomStatus currentStatus) {
        super("Transición de estado inválida para la habitación en estado: " + currentStatus);
        this.currentStatus = currentStatus;
    }

    public InvalidRoomTransitionException(RoomStatus currentStatus, String message) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public InvalidRoomTransitionException(RoomStatus currentStatus, String message, Throwable cause) {
        super(message, cause);
        this.currentStatus = currentStatus;
    }

    public RoomStatus getCurrentStatus() {
        return currentStatus;
    }
}
