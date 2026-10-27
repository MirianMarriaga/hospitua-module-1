package com.hospitua.habitaciones.domain.exception;

/**
 * Excepción lanzada cuando una tarea operativa ya fue confirmada o liberada (HTTP 409 TASK_ALREADY_CLOSED).
 */
public class TaskAlreadyClosedException extends DomainException {

    public TaskAlreadyClosedException(String message) {
        super(message);
    }

    public TaskAlreadyClosedException() {
        super("Esta tarea ya fue cerrada.");
    }
}
