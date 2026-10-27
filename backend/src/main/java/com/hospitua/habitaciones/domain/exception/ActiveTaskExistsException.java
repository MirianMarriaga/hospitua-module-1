package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando un miembro del personal ya tiene una tarea activa abierta (HTTP 409 ACTIVE_TASK_EXISTS).
 */
public class ActiveTaskExistsException extends DomainException {

    public ActiveTaskExistsException(String message) {
        super(message);
    }

    public ActiveTaskExistsException() {
        super("El miembro del personal ya tiene una tarea activa.");
    }
}
