package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.domain.exception.InvalidDamageDescriptionException;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/rooms/{roomId}/damage-reports}: solo la descripción. Cualquier fecha que envíe el cliente
 * se ignora (FR-008, caso límite 4).
 */
public record ReportDamageRequest(
        @NotBlank(message = InvalidDamageDescriptionException.MESSAGE)
        @Size(max = 500, message = InvalidDamageDescriptionException.MESSAGE)
        String damageDescription) {

    public ReportDamageCommand toCommand(UUID roomId, String authorId) {
        return new ReportDamageCommand(roomId, damageDescription, authorId, SourceFlow.REPORT_DAMAGE);
    }
}
