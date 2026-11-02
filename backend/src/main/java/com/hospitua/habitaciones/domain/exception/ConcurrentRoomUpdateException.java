package com.hospitua.habitaciones.domain.exception;

/**
 * La {@code version} con la que se abrió el formulario de edición ya no es la de la habitación: otro usuario la cambió
 * (409 CONCURRENT_UPDATE, plan-editar-habitacion.md, caso borde "Ediciones simultáneas").
 */
public final class ConcurrentRoomUpdateException extends DomainException {

    public static final String MESSAGE =
            "La información de la habitación ya fue actualizada por otro usuario. Recarga para ver los datos actuales.";

    public ConcurrentRoomUpdateException() {
        super(DomainError.CONCURRENT_UPDATE, MESSAGE);
    }
}
