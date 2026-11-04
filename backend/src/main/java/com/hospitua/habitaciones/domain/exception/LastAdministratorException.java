package com.hospitua.habitaciones.domain.exception;

/**
 * Es la última cuenta activa del área Administración (409 LAST_ADMINISTRATOR; plan-gestionar-personal.md).
 */
public final class LastAdministratorException extends DomainException {

    public LastAdministratorException() {
        super(DomainError.LAST_ADMINISTRATOR, "No se puede desactivar: es la única cuenta activa de Administración.");
    }
}
