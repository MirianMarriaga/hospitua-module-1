package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * El {@code decommissionId} de la reactivación no es una baja de esa habitación (400 VALIDATION_ERROR,
 * {@code field = decommissionId}): la confirmación quedó desactualizada (plan-marcar-habitacion-como-disponible.md).
 */
public final class UnknownDecommissionException extends DomainException {

    public static final String MESSAGE =
            "No se pudo identificar la baja de la habitación. Vuelve a abrir la confirmación.";

    public UnknownDecommissionException() {
        super(DomainError.VALIDATION_ERROR, MESSAGE, Map.of("field", "decommissionId"));
    }
}
