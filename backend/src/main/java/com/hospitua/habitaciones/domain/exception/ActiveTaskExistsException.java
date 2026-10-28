package com.hospitua.habitaciones.domain.exception;

/**
 * El miembro del personal ya tiene una tarea abierta (409 ACTIVE_TASK_EXISTS).
 */
public final class ActiveTaskExistsException extends DomainException {

    public ActiveTaskExistsException() {
        this("El miembro del personal ya tiene una tarea activa.");
    }

    public ActiveTaskExistsException(String message) {
        super(DomainError.ACTIVE_TASK_EXISTS, message);
    }
}
