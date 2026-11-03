package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import java.util.UUID;

/**
 * Respuesta de {@code POST /api/rooms/{roomId}/technical-blocks}: el informe creado y su estado, {@code Scheduled} o,
 * si inicia hoy, {@code Applied} (la habitación quedó en {@code TechnicalBlock}).
 */
public record TechnicalBlockCreatedDto(UUID technicalBlockId, TechnicalBlockStatus status) {

    public static TechnicalBlockCreatedDto of(TechnicalBlockReport report) {
        return new TechnicalBlockCreatedDto(report.getId(), report.getStatus());
    }
}
