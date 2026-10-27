package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando una tarea operativa pertenece a otro miembro del personal (HTTP 403 TASK_NOT_OWNED).
 */
public class TaskNotOwnedException extends DomainException {

    public TaskNotOwnedException(String message) {
        super(message);
    }

    public TaskNotOwnedException() {
        super("La tarea pertenece a otro miembro del personal.");
    }
}
