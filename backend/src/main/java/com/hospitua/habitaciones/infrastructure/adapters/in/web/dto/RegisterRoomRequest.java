package com.hospitua.habitaciones.infrastructure.adapters.in.web.dto;

import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Cuerpo de POST /api/rooms (plan-registrar-habitacion.md). No acepta {@code roomId} ni {@code status}: si llegan,
 * se ignoran (FR-002, FR-008). La categoría llega como texto para responder el mensaje del catálogo (FR-005), y el
 * piso y la capacidad como número decimal para rechazar un valor no entero en vez de truncarlo (FR-006, FR-011).
 */
public record RegisterRoomRequest(
        @NotBlank(message = REQUIRED) @Size(max = Room.MAX_ROOM_NUMBER_LENGTH, message = ROOM_NUMBER_RULE)
        String roomNumber,

        @NotNull(message = REQUIRED)
        @Positive(message = FLOOR_RULE)
        @Digits(integer = 9, fraction = 0, message = FLOOR_RULE)
        BigDecimal floor,

        @NotNull(message = REQUIRED)
        @Pattern(
                regexp = "SENCILLA|DOBLE|SUITE|BOUTIQUE",
                message = "Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique.")
        String categoryRoom,

        @NotNull(message = REQUIRED)
        @Positive(message = CAPACITY_RULE)
        @Digits(integer = 9, fraction = 0, message = CAPACITY_RULE)
        BigDecimal maxCapacity,

        @NotNull(message = REQUIRED)
        @Positive(message = BASE_RATE_RULE)
        @Digits(integer = 10, fraction = 2, message = BASE_RATE_RULE)
        BigDecimal baseRate) {

    private static final String REQUIRED = "Completa los campos obligatorios.";
    private static final String ROOM_NUMBER_RULE = "El número de habitación debe tener máximo 10 caracteres.";
    private static final String FLOOR_RULE = "El piso debe ser un número entero mayor a 0.";
    private static final String CAPACITY_RULE = "La capacidad máxima debe ser un número entero mayor a 0.";
    private static final String BASE_RATE_RULE = "La tarifa base debe ser mayor a 0 y tener máximo dos decimales.";

    public RegisterRoomCommand toCommand(String administratorId) {
        return new RegisterRoomCommand(
                roomNumber,
                floor.intValueExact(),
                RoomCategory.valueOf(categoryRoom),
                maxCapacity.intValueExact(),
                baseRate,
                administratorId);
    }
}
