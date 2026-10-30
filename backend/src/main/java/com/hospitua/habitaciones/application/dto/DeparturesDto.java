package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.DepartureSummary;
import java.util.List;

/** Respuesta de {@code GET /api/reception/departures}: primero las salidas vencidas (FR-004, FR-007). */
public record DeparturesDto(List<DepartureSummary> stays) {

    public DeparturesDto {
        stays = List.copyOf(stays);
    }
}
