package com.hospitua.habitaciones.domain.exception;

/**
 * La habitación no tiene un informe de bloqueo técnico {@code Scheduled} ni {@code Applied}: "Ver informe" no tiene
 * qué mostrar (404 TECHNICAL_BLOCK_NOT_FOUND; plan-programar-bloqueo-tecnico-para-habitacion.md FR-009).
 */
public final class TechnicalBlockNotFoundException extends DomainException {

    public TechnicalBlockNotFoundException() {
        super(
                DomainError.TECHNICAL_BLOCK_NOT_FOUND,
                "Esta habitación no tiene un mantenimiento programado ni en curso.");
    }
}
