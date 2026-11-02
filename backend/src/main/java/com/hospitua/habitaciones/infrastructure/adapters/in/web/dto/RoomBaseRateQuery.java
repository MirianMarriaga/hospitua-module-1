package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Parámetro {@code date} de {@code GET /api/rooms/{roomId}/base-rate} (plan-consultar-tarifa-base.md T009, T012). Llega
 * como texto para responder un solo mensaje, con {@code details.field = date}, si falta, si no es AAAA-MM-DD o si el día
 * no existe en el calendario (p. ej. 2026-02-31).
 */
public record RoomBaseRateQuery(String date) {

    static final String DATE_FORMAT_RULE = "La fecha debe tener un formato válido (AAAA-MM-DD).";

    /** Propiedad {@code date} para Bean Validation: el texto es una fecha ISO existente. */
    @AssertTrue(message = DATE_FORMAT_RULE)
    public boolean isDate() {
        return parse() != null;
    }

    public LocalDate toDate() {
        return parse();
    }

    private LocalDate parse() {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
