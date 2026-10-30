package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Parámetros de GET /api/room-state-history (plan-consultar-historial-de-estados.md). Todos son opcionales; sin
 * {@code page} es la 1. Las fechas llegan como {@code YYYY-MM-DD}.
 */
public record RoomStateHistoryQuery(
        UUID roomId,
        String roomNumber,
        RoomCategory categoryRoom,
        RoomStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

        @Min(value = 1, message = "Revisa los filtros aplicados.")
        Integer page) {

    public RoomStateHistoryFilter toFilter() {
        return new RoomStateHistoryFilter(roomId, roomNumber, categoryRoom, status, from, to);
    }

    public int pageOrDefault() {
        return Objects.requireNonNullElse(page, 1);
    }
}
