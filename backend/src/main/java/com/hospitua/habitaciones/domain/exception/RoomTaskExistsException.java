package com.hospitua.habitaciones.domain.exception;

/**
 * La habitación ya tiene una tarea abierta (409 ROOM_TASK_EXISTS).
 */
public final class RoomTaskExistsException extends DomainException {

    public RoomTaskExistsException() {
        this("La habitación ya tiene una tarea activa.");
    }

    public RoomTaskExistsException(String message) {
        super(DomainError.ROOM_TASK_EXISTS, message);
    }
}
