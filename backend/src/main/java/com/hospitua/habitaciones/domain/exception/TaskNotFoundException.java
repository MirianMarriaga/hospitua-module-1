package com.hospitua.habitaciones.domain.exception;

/**
 * La tarea operativa no existe (404 TASK_NOT_FOUND).
 */
public final class TaskNotFoundException extends DomainException {

    public TaskNotFoundException() {
        this("No se encontró la tarea especificada.");
    }

    public TaskNotFoundException(String message) {
        super(DomainError.TASK_NOT_FOUND, message);
    }
}
