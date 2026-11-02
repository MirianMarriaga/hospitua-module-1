package com.hospitua.habitaciones.domain.exception;

import java.util.Map;

/**
 * El detalle del motivo de la baja no cumple sus reglas (400 VALIDATION_ERROR, {@code field = reasonDetail}): falta con
 * la categoría {@code OTHER} o supera los 500 caracteres (plan-dar-de-baja-habitacion.md, FR-008).
 */
public final class InvalidDecommissionReasonException extends DomainException {

    public static final String DETAIL_REQUIRED = "Escribe el motivo de la baja.";
    public static final String DETAIL_TOO_LONG = "El detalle admite máximo 500 caracteres.";

    public InvalidDecommissionReasonException(String message) {
        super(DomainError.VALIDATION_ERROR, message, Map.of("field", "reasonDetail"));
    }
}
