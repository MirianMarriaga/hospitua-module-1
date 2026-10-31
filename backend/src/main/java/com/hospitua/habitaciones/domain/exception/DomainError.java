package com.hospitua.habitaciones.domain.exception;

/**
 * Errores de dominio del catálogo de errores del plan base: cada valor es el {@code errorCode} que recibe el cliente
 * y lleva su categoría.
 */
public enum DomainError {
    ROOM_NOT_FOUND(ErrorCategory.NOT_FOUND),
    TASK_NOT_FOUND(ErrorCategory.NOT_FOUND),
    RESERVATION_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ACTIVE_STAY_NOT_FOUND(ErrorCategory.NOT_FOUND),
    ROOM_INVALID_STATE(ErrorCategory.CONFLICT),
    TASK_ALREADY_CLOSED(ErrorCategory.CONFLICT),
    ACTIVE_TASK_EXISTS(ErrorCategory.CONFLICT),
    ROOM_TASK_EXISTS(ErrorCategory.CONFLICT),
    USERNAME_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    ROOM_NUMBER_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    RESERVATION_CONFLICT(ErrorCategory.CONFLICT),
    INVALID_CHECK_IN_DATE(ErrorCategory.CONFLICT),
    STAY_ALREADY_EXISTS(ErrorCategory.CONFLICT),
    TASK_NOT_OWNED(ErrorCategory.FORBIDDEN),
    CURRENT_USER_REQUIRED(ErrorCategory.INVALID_REQUEST),
    VALIDATION_ERROR(ErrorCategory.INVALID_REQUEST),
    INVALID_DATE_RANGE(ErrorCategory.INVALID_REQUEST),
    GUEST_COUNT_MISMATCH(ErrorCategory.INVALID_REQUEST),
    ROOM_CAPACITY_EXCEEDED(ErrorCategory.INVALID_REQUEST),
    DUPLICATE_GUEST_DOCUMENT(ErrorCategory.INVALID_REQUEST),
    INVALID_CREDENTIALS(ErrorCategory.UNAUTHORIZED),
    MODULE_2_UNAVAILABLE(ErrorCategory.DEPENDENCY_FAILED),
    MODULE_3_UNAVAILABLE(ErrorCategory.DEPENDENCY_FAILED);

    private final ErrorCategory category;

    DomainError(ErrorCategory category) {
        this.category = category;
    }

    public ErrorCategory category() {
        return category;
    }
}
