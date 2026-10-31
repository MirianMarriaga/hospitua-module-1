package com.hospitua.habitaciones.application.dto;

import java.util.Objects;
import java.util.UUID;

/**
 * Confirmar fin de limpieza (plan-confirmar-fin-de-limpieza.md, Structure Decision): {@code memberId} es el usuario
 * actual. Una descripción vacía o solo con espacios equivale a no reportar daño y queda nula (FR-018).
 */
public record CompleteCleaningCommand(UUID taskId, String memberId, String damageDescription) {

    public CompleteCleaningCommand {
        Objects.requireNonNull(taskId, "El id de la tarea no puede ser nulo");
        Objects.requireNonNull(memberId, "El miembro no puede ser nulo");
        if (damageDescription != null && damageDescription.isBlank()) {
            damageDescription = null;
        }
    }

    public boolean reportsDamage() {
        return damageDescription != null;
    }
}
