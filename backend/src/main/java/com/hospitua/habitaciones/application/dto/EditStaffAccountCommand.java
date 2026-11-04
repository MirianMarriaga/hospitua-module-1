package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.StaffArea;

/**
 * Edición de una cuenta del personal (plan-gestionar-personal.md T027). {@code username} sale de la ruta y no cambia
 * (FR-008); {@code rawPassword} nula o vacía conserva la actual (esc. 7); {@code administratorId} es el usuario actual
 * (FR-013). Sin anotaciones de validación (regla 1 del plan base).
 */
public record EditStaffAccountCommand(
        String username, Integer version, String fullName, StaffArea area, String rawPassword, String administratorId) {

    /** Nunca muestra la contraseña, ni en un log que imprima el comando (FR-004; SC-002). */
    @Override
    public String toString() {
        return "EditStaffAccountCommand[username=" + username + ", version=" + version + ", fullName=" + fullName
                + ", area=" + area + ", administratorId=" + administratorId + "]";
    }
}
