package com.hospitua.habitaciones.domain.model;

/**
 * Catálogo canónico de flujos de origen para el historial de transiciones de habitación.
 */
public enum SourceFlow {
    REGISTER_ROOM("Registrar habitación"),
    MARK_RESERVED("Marcar habitación como reservada"),
    MARK_AVAILABLE("Marcar habitación como disponible"),
    CHECK_IN("Registrar check-in"),
    CHECK_OUT("Registrar check-out"),
    START_CLEANING("Marcar habitación en limpieza"),
    CONFIRM_CLEANING_END("Confirmar fin de limpieza"),
    RELEASE_CLEANING_TASK("Confirmar fin de limpieza (liberar tarea)"),
    REPORT_DAMAGE("Marcar habitación inhabilitada por reparaciones"),
    CONFIRM_REPAIR_END("Confirmar reparación finalizada"),
    TECHNICAL_BLOCK("Programar bloqueo técnico para habitación"),
    DECOMMISSION_ROOM("Dar de baja habitación");

    private final String displayName;

    SourceFlow(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
