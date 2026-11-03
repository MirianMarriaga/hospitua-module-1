package com.hospitua.habitaciones.application.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Respuesta de {@code GET /api/rooms/{roomId}/technical-blocks/current}: "Ver informe" (FR-009, FR-010). */
public record TechnicalBlockViewDto(
        String roomNumber,
        String reason,
        String responsibleName,
        LocalDate startDate,
        LocalDate estimatedEndDate,
        OffsetDateTime reportDateTime) {}
