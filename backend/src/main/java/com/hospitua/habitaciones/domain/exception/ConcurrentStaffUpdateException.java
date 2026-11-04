package com.hospitua.habitaciones.domain.exception;

/**
 * La {@code version} con la que se abrió el formulario de edición ya no es la de la cuenta: otro Administrador la
 * cambió (409 CONCURRENT_UPDATE, plan-gestionar-personal.md, caso borde "Ediciones simultáneas").
 */
public final class ConcurrentStaffUpdateException extends DomainException {

    public static final String MESSAGE =
            "La información de la cuenta ya fue actualizada por otro usuario. Recarga para ver los datos actuales.";

    public ConcurrentStaffUpdateException() {
        super(DomainError.CONCURRENT_UPDATE, MESSAGE);
    }
}
