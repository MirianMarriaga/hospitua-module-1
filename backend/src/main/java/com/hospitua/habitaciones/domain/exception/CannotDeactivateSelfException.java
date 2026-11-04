package com.hospitua.habitaciones.domain.exception;

/**
 * El Administrador intenta desactivar su propia cuenta (409 CANNOT_DEACTIVATE_SELF; plan-gestionar-personal.md).
 */
public final class CannotDeactivateSelfException extends DomainException {

    public CannotDeactivateSelfException() {
        super(DomainError.CANNOT_DEACTIVATE_SELF, "No puedes desactivar tu propia cuenta.");
    }
}
