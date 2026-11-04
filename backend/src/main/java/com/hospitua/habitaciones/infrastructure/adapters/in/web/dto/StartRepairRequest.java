package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.StartRepairCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/maintenance/tasks}: solo la habitación. Cualquier fecha que envíe el cliente se ignora
 * (FR-011).
 */
public record StartRepairRequest(
        @NotNull(message = "Selecciona una habitación válida.")
        UUID roomId) {

    public StartRepairCommand toCommand(String memberId) {
        return new StartRepairCommand(roomId, memberId);
    }
}
