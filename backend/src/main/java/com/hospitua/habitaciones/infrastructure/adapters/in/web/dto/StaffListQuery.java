package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.StaffAccountFilter;
import com.hospitua.habitaciones.domain.model.StaffArea;
import jakarta.validation.constraints.Pattern;

/**
 * Parámetros de {@code GET /api/staff} (plan-gestionar-personal.md T021). Todos son opcionales y combinables; sin
 * {@code page} es la 1. El área, el estado y la página llegan como texto para responder siempre el mismo mensaje,
 * también con una página que no es un número.
 */
public record StaffListQuery(
        @Pattern(regexp = "RECEPCION|ADMINISTRACION|GERENCIA|LIMPIEZA|MANTENIMIENTO", message = INVALID)
        String area,

        @Pattern(regexp = "true|false", message = INVALID) String active,
        String query,

        @Pattern(regexp = "[1-9][0-9]{0,8}", message = INVALID)
        String page) {

    private static final String INVALID = "Revisa los filtros aplicados.";

    public StaffAccountFilter toFilter() {
        return new StaffAccountFilter(
                area == null ? null : StaffArea.valueOf(area),
                active == null ? null : Boolean.valueOf(active),
                query == null || query.isBlank() ? null : query.trim());
    }

    public int pageOrDefault() {
        return page == null ? 1 : Integer.parseInt(page);
    }
}
