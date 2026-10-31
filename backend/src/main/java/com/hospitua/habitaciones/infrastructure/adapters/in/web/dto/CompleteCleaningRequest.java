package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.CompleteCleaningCommand;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo opcional de {@code POST /api/cleaning/tasks/{taskId}/complete}: la descripción del daño encontrado, si lo hay
 * (FR-018). Cualquier fecha que envíe el cliente se ignora (FR-007).
 */
public record CompleteCleaningRequest(
        @Size(max = 500, message = "La descripción del daño admite máximo 500 caracteres.")
        String damageDescription) {

    public static CompleteCleaningCommand toCommand(CompleteCleaningRequest request, UUID taskId, String memberId) {
        return new CompleteCleaningCommand(taskId, memberId, request == null ? null : request.damageDescription());
    }
}
