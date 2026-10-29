package com.hospitua.habitaciones.domain.exception;

/**
 * La tarea ya fue confirmada o liberada (409 TASK_ALREADY_CLOSED).
 */
public final class TaskAlreadyClosedException extends DomainException {

    public TaskAlreadyClosedException() {
        this("Esta tarea ya fue cerrada.");
    }

    public TaskAlreadyClosedException(String message) {
        super(DomainError.TASK_ALREADY_CLOSED, message);
    }
}
