package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.Room;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/rooms/{roomId}/base-rate} para Módulo 3 (plan-consultar-tarifa-base.md T003): la tarifa
 * base como texto con dos decimales, la fecha recibida tal cual y la moneda. No expone el resto de la habitación
 * (FR-003).
 */
public record RoomBaseRateResponseDto(UUID roomId, LocalDate date, String baseRate, String currency) {

    public static final String CURRENCY = "COP";

    public static RoomBaseRateResponseDto of(Room room, LocalDate date) {
        return new RoomBaseRateResponseDto(
                room.getId(),
                date,
                room.getBaseRate().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                CURRENCY);
    }
}
