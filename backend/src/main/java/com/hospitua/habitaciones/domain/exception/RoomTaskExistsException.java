package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando la habitación ya tiene una tarea abierta de otro miembro (HTTP 409 ROOM_TASK_EXISTS).
 */
public class RoomTaskExistsException extends DomainException {

    public RoomTaskExistsException(String message) {
        super(message);
    }

    public RoomTaskExistsException() {
        super("La habitación ya tiene una tarea activa.");
    }
}
