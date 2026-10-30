package com.hospitua.habitaciones.domain.exception;

/**
 * Errores de dominio del catálogo de errores del plan base: cada valor es el {@code errorCode} que recibe el cliente
 * y lleva su categoría.
 */
public enum DomainError {
    ROOM_NOT_FOUND(ErrorCategory.NOT_FOUND),
    TASK_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ROOM_INVALID_STATE(ErrorCategory.CONFLICT),
    TASK_ALREADY_CLOSED(ErrorCategory.CONFLICT),
    ACTIVE_TASK_EXISTS(ErrorCategory.CONFLICT),
    ROOM_TASK_EXISTS(ErrorCategory.CONFLICT),
    USERNAME_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    ROOM_NUMBER_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    RESERVATION_CONFLICT(ErrorCategory.CONFLICT),
    TASK_NOT_OWNED(ErrorCategory.FORBIDDEN),
    CURRENT_USER_REQUIRED(ErrorCategory.INVALID_REQUEST),
    VALIDATION_ERROR(ErrorCategory.INVALID_REQUEST),
    INVALID_DATE_RANGE(ErrorCategory.INVALID_REQUEST),
    INVALID_CREDENTIALS(ErrorCategory.UNAUTHORIZED),
    MODULE_2_UNAVAILABLE(ErrorCategory.UNAVAILABLE);

    private final ErrorCategory category;

    DomainError(ErrorCategory category) {
        this.category = category;
    }

    public ErrorCategory category() {
        return category;
    }
}
