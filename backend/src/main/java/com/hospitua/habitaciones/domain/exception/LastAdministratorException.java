package com.hospitua.habitaciones.domain.exception;

/**
 * La operación dejaría el sistema sin cuentas activas del área Administración (409 LAST_ADMINISTRATOR;
 * plan-gestionar-personal.md, FR-010): cambiarle el área a la única que queda o desactivarla. Cada operación pone su
 * mensaje.
 */
public final class LastAdministratorException extends DomainException {

    public LastAdministratorException(String message) {
        super(DomainError.LAST_ADMINISTRATOR, message);
    }
}
