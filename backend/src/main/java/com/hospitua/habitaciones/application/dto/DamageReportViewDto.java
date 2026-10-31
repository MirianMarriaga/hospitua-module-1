package com.hospitua.habitaciones.application.dto;

import java.time.OffsetDateTime;

/** Respuesta de {@code GET /api/rooms/{roomId}/damage-reports/latest}: "Ver informe" (FR-006, FR-007). */
public record DamageReportViewDto(
        String roomNumber, String damageDescription, String authorName, OffsetDateTime reportDateTime) {}
