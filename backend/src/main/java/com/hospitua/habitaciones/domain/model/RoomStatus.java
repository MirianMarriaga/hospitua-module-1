package com.hospitua.habitaciones.domain.model;

/**
 * Estados canónicos del ciclo de vida de una habitación física. {@link #displayName()} es el nombre en español que
 * muestra la interfaz (regla 12 del plan base); la API, el código y los datos usan el nombre del enum.
 */
public enum RoomStatus {
    Available("Disponible"),
    Reserved("Reservada"),
    Occupied("Ocupada"),
    PendingCleaning("Pendiente de limpieza"),
    InCleaning("En limpieza"),
    DisabledForRepairs("Inhabilitada por reparaciones"),
    TechnicalBlock("Bloqueo técnico"),
    Inactive("Inactiva");

    private final String displayName;

    RoomStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
