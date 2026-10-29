package com.hospitua.habitaciones.domain.exception;

/**
 * La tarea pertenece a otro miembro del personal (403 TASK_NOT_OWNED).
 */
public final class TaskNotOwnedException extends DomainException {

    public TaskNotOwnedException() {
        this("La tarea pertenece a otro miembro del personal.");
    }

    public TaskNotOwnedException(String message) {
        super(DomainError.TASK_NOT_OWNED, message);
    }
}
