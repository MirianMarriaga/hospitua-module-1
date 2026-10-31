package com.hospitua.habitaciones.domain.exception;

/** La casilla de confirmación del Paso 4 no está marcada (400 CONFIRMATION_REQUIRED; registrar-check-out FR-008). */
public final class MandatoryConfirmationMissingException extends DomainException {

    public MandatoryConfirmationMissingException() {
        super(
                DomainError.CONFIRMATION_REQUIRED,
                "Confirma la salida del huésped y que la información mostrada es correcta.");
    }
}
