package com.hospitua.habitaciones.domain.model;

/**
 * Categoría del motivo de una baja (plan-dar-de-baja-habitacion.md, FR-008). Solo {@code OTHER} exige escribir el
 * detalle.
 */
public enum DecommissionReason {
    PERMANENT_REMODELING,
    FLOOR_CLOSURE,
    ADMINISTRATIVE_DECISION,
    OTHER;

    public boolean requiresDetail() {
        return this == OTHER;
    }
}
