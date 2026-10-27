package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando una tarea operativa no existe (HTTP 404 TASK_NOT_FOUND).
 */
public class TaskNotFoundException extends DomainException {

    public TaskNotFoundException(String message) {
        super(message);
    }

    public TaskNotFoundException() {
        super("No se encontró la tarea especificada.");
    }
}
