package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RoomStateHistoryFilter;
import com.hospitua.habitaciones.application.dto.RoomStateHistorySort;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Parámetros de GET /api/room-state-history (plan-consultar-historial-de-estados.md). Todos son opcionales; sin
 * {@code page} es la 1 y sin {@code sort} ni {@code direction}, por fecha de inicio ascendente. Las fechas llegan como
 * {@code YYYY-MM-DD}.
 */
public record RoomStateHistoryQuery(
        UUID roomId,
        String roomNumber,
        RoomCategory categoryRoom,
        RoomStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,

        @Pattern(regexp = "startDateTime|endDateTime", message = INVALID)
        String sort,

        @Pattern(regexp = "asc|desc", message = INVALID) String direction,
        @Min(value = 1, message = INVALID) Integer page) {

    private static final String INVALID = "Revisa los filtros aplicados.";

    public RoomStateHistoryFilter toFilter() {
        RoomStateHistorySort column =
                "endDateTime".equals(sort) ? RoomStateHistorySort.END_DATE_TIME : RoomStateHistorySort.START_DATE_TIME;
        return new RoomStateHistoryFilter(
                roomId, roomNumber, categoryRoom, status, from, to, column, "desc".equals(direction));
    }

    public int pageOrDefault() {
        return Objects.requireNonNullElse(page, 1);
    }
}
