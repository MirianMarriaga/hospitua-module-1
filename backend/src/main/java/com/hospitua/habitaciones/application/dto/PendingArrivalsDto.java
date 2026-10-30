package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.ArrivalGroup;
import java.util.List;

/**
 * Respuesta de {@code GET /api/reception/arrivals}. {@code listAvailable} es {@code false} si hoy no llegó la lista de
 * las 00:00, y entonces {@code groups} va vacío (plan-consultar-panel-recepcion.md, FR-011).
 */
public record PendingArrivalsDto(boolean listAvailable, List<ArrivalGroup> groups) {

    public PendingArrivalsDto {
        groups = List.copyOf(groups);
    }

    public static PendingArrivalsDto listNotReceived() {
        return new PendingArrivalsDto(false, List.of());
    }
}
