package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.StaffAccountFilter;
import com.hospitua.habitaciones.domain.model.StaffArea;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

/**
 * Parámetros de {@code GET /api/staff} (plan-gestionar-personal.md T021). Todos son opcionales y combinables; sin
 * {@code page} es la 1. El área y el estado llegan como texto para responder siempre el mismo mensaje.
 */
public record StaffListQuery(
        @Pattern(regexp = "RECEPCION|ADMINISTRACION|GERENCIA|LIMPIEZA|MANTENIMIENTO", message = INVALID)
        String area,

        @Pattern(regexp = "true|false", message = INVALID) String active,
        String query,
        @Min(value = 1, message = INVALID) Integer page) {

    private static final String INVALID = "Revisa los filtros aplicados.";

    public StaffAccountFilter toFilter() {
        return new StaffAccountFilter(
                area == null ? null : StaffArea.valueOf(area),
                active == null ? null : Boolean.valueOf(active),
                query == null || query.isBlank() ? null : query.trim());
    }

    public int pageOrDefault() {
        return Objects.requireNonNullElse(page, 1);
    }
}
