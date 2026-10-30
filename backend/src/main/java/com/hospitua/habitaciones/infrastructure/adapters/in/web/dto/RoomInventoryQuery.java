package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RoomInventoryFilter;
import com.hospitua.habitaciones.application.dto.RoomInventorySort;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.Objects;

/**
 * Parámetros de GET /api/rooms/inventory (plan-consultar-inventario-habitaciones.md). Todos son opcionales: sin
 * {@code page} es la 1 y sin {@code size} son 10 por página (FR-009).
 */
public record RoomInventoryQuery(
        String roomNumber,
        RoomCategory categoryRoom,
        @Positive(message = INVALID) Integer floor,
        RoomStatus status,

        @Pattern(regexp = "roomNumber|floor|categoryRoom|maxCapacity|baseRate|status", message = INVALID)
        String sort,

        @Pattern(regexp = "asc|desc", message = INVALID) String direction,
        @Min(value = 1, message = INVALID) Integer page,

        @Min(value = 1, message = INVALID) @Max(value = 100, message = INVALID)
        Integer size) {

    private static final String INVALID = "Revisa los filtros aplicados.";
    private static final int DEFAULT_SIZE = 10;

    public RoomInventoryFilter toFilter() {
        return new RoomInventoryFilter(roomNumber, categoryRoom, floor, status, sortColumn(), "desc".equals(direction));
    }

    public int pageOrDefault() {
        return Objects.requireNonNullElse(page, 1);
    }

    public int sizeOrDefault() {
        return Objects.requireNonNullElse(size, DEFAULT_SIZE);
    }

    private RoomInventorySort sortColumn() {
        if (sort == null) {
            return RoomInventorySort.ROOM_NUMBER;
        }
        return switch (sort) {
            case "floor" -> RoomInventorySort.FLOOR;
            case "categoryRoom" -> RoomInventorySort.CATEGORY_ROOM;
            case "maxCapacity" -> RoomInventorySort.MAX_CAPACITY;
            case "baseRate" -> RoomInventorySort.BASE_RATE;
            case "status" -> RoomInventorySort.STATUS;
            default -> RoomInventorySort.ROOM_NUMBER;
        };
    }
}
