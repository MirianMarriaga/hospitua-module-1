package com.hospitua.habitaciones.domain.exception;

/**
 * No existe una cuenta con ese usuario (404 STAFF_NOT_FOUND; plan-gestionar-personal.md).
 */
public final class StaffAccountNotFoundException extends DomainException {

    public StaffAccountNotFoundException() {
        super(DomainError.STAFF_NOT_FOUND, "No se encontró la cuenta.");
    }
}
