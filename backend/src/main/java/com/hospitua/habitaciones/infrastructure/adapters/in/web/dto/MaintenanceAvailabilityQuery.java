package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Habitación y estadía de {@code GET /api/rooms/{roomId}/maintenances} (plan-consultar-informacion-mantenimientos.md
 * T012, T014). Llegan como texto para responder siempre el mismo mensaje, con {@code details.field}, si falta un dato,
 * si {@code roomId} no es un UUID o si una fecha no es AAAA-MM-DD o no existe en el calendario (FR-005, FR-013).
 * {@code roomId} sale de la ruta: el enlace de datos de Spring incluye las variables de la URI.
 */
public record MaintenanceAvailabilityQuery(String roomId, String startDate, String endDate) {

    static final String FORMAT_RULE = "La habitación y las fechas deben tener un formato válido (fechas AAAA-MM-DD).";

    // El contrato OpenAPI muestra el campo del mismo nombre como texto, no este booleano de validación (L40).
    @Schema(implementation = String.class)
    @AssertTrue(message = FORMAT_RULE)
    public boolean isRoomId() {
        return parseUuid() != null;
    }

    // El contrato OpenAPI muestra el campo del mismo nombre como texto, no este booleano de validación (L40).
    @Schema(implementation = String.class)
    @AssertTrue(message = FORMAT_RULE)
    public boolean isStartDate() {
        return parseDate(startDate) != null;
    }

    // El contrato OpenAPI muestra el campo del mismo nombre como texto, no este booleano de validación (L40).
    @Schema(implementation = String.class)
    @AssertTrue(message = FORMAT_RULE)
    public boolean isEndDate() {
        return parseDate(endDate) != null;
    }

    public UUID toRoomId() {
        return parseUuid();
    }

    /** Primer día evaluado: la llegada. */
    public LocalDate firstDay() {
        return parseDate(startDate);
    }

    /**
     * Último día evaluado (FR-003): el anterior a la salida, que no se considera ocupada; si la salida es el mismo día
     * de la llegada, ese día. Una salida anterior a la llegada da un último día anterior al primero, y la consulta la
     * rechaza con {@code END_BEFORE_START}; por eso no se usa {@code max(llegada, salida − 1)}, que la ocultaría.
     */
    public LocalDate lastDay() {
        LocalDate arrival = parseDate(startDate);
        LocalDate departure = parseDate(endDate);
        return departure.equals(arrival) ? arrival : departure.minusDays(1);
    }

    private UUID parseUuid() {
        if (roomId == null
                || !roomId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            return null;
        }
        return UUID.fromString(roomId);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
