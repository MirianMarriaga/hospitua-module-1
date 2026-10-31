package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reporte de un daño (plan-marcar-habitacion-inhabilitada-por-reparaciones.md, Structure Decision): {@code authorId} es
 * el usuario actual o el del flujo invocador, y {@code sourceFlow} es {@code REPORT_DAMAGE} desde el endpoint o
 * {@code CONFIRM_CLEANING_END} desde el fin de limpieza con daño. No lleva fechas: la marca la pone el servidor
 * (FR-008).
 */
public record ReportDamageCommand(UUID roomId, String damageDescription, String authorId, SourceFlow sourceFlow) {

    private static final Set<SourceFlow> SOURCE_FLOWS =
            Set.of(SourceFlow.REPORT_DAMAGE, SourceFlow.CONFIRM_CLEANING_END);

    public ReportDamageCommand {
        Objects.requireNonNull(roomId, "El id de la habitación no puede ser nulo");
        Objects.requireNonNull(authorId, "El autor no puede ser nulo");
        if (!SOURCE_FLOWS.contains(sourceFlow)) {
            throw new IllegalArgumentException("Un daño se reporta solo desde " + SOURCE_FLOWS + ": " + sourceFlow);
        }
    }
}
