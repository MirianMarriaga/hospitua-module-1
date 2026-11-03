package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.ScheduleTechnicalBlockCommand;
import com.hospitua.habitaciones.domain.exception.InvalidTechnicalBlockReasonException;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/rooms/{roomId}/technical-blocks} (plan-programar-bloqueo-tecnico-para-habitacion.md). Una
 * fecha que no es AAAA-MM-DD o no existe en el calendario (31-02) la rechaza Jackson con {@code details.field}; la
 * interfaz muestra el texto según ese campo. El dominio vuelve a validar y recorta la justificación.
 */
public record ScheduleTechnicalBlockRequest(
        @NotBlank(message = InvalidTechnicalBlockReasonException.MESSAGE)
        @Size(max = TechnicalBlockReport.MAX_REASON_LENGTH, message = InvalidTechnicalBlockReasonException.MESSAGE)
        String reason,

        @NotNull(message = DATE_REQUIRED) LocalDate startDate,

        @NotNull(message = DATE_REQUIRED) LocalDate estimatedEndDate) {

    static final String DATE_REQUIRED = "Elige una fecha válida (DD-MM-YYYY).";

    public ScheduleTechnicalBlockCommand toCommand(UUID roomId, String memberId) {
        return new ScheduleTechnicalBlockCommand(roomId, memberId, reason, startDate, estimatedEndDate);
    }
}
