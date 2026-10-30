package com.hospitua.habitaciones.domain.exception;

import java.util.Objects;

/**
 * Módulo 3 no entregó la liquidación (consultar-liquidacion T004, T022). No llega al recepcionista como error: la
 * consulta la convierte en la respuesta controlada {@code UNAVAILABLE} con {@link #unavailableCause()}, que es el
 * {@code errorCode} de Módulo 3 o una de las causas propias ({@code HTTP_ERROR}, {@code TIMEOUT},
 * {@code CONNECTION_ERROR}, {@code INVALID_RESPONSE}, {@code CIRCUIT_OPEN}).
 */
public final class ExternalModule3UnavailableException extends DomainException {

    public static final String HTTP_ERROR = "HTTP_ERROR";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String CONNECTION_ERROR = "CONNECTION_ERROR";
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    public static final String CIRCUIT_OPEN = "CIRCUIT_OPEN";

    private final String unavailableCause;

    public ExternalModule3UnavailableException(String unavailableCause, Throwable cause) {
        super(DomainError.MODULE_3_UNAVAILABLE, "Servicio de facturación temporalmente no disponible.");
        this.unavailableCause = Objects.requireNonNull(unavailableCause, "La causa no puede ser nula");
        initCause(cause);
    }

    public String unavailableCause() {
        return unavailableCause;
    }
}
