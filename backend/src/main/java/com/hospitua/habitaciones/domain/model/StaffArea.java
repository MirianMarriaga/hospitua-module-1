package com.hospitua.habitaciones.domain.model;

/**
 * Catálogo canónico de áreas operativas del personal de Módulo 1 (T022).
 */
public enum StaffArea {
    RECEPCION("Recepción"),
    ADMINISTRACION("Administración"),
    GERENCIA("Gerencia"),
    LIMPIEZA("Limpieza"),
    MANTENIMIENTO("Mantenimiento");

    private final String displayName;

    StaffArea(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
