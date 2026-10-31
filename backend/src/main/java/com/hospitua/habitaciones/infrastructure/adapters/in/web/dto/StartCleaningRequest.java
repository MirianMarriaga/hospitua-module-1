package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.StartCleaningCommand;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/cleaning/tasks}: solo la habitación. Cualquier fecha que envíe el cliente se ignora
 * (FR-006, caso límite 3).
 */
public record StartCleaningRequest(
        @NotNull(message = "Selecciona una habitación válida.")
        UUID roomId) {

    public StartCleaningCommand toCommand(String memberId) {
        return new StartCleaningCommand(roomId, memberId);
    }
}
