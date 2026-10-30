package com.hospitua.habitaciones.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Parámetros de {@code GET /api/settlements} de Módulo 3 (consultar-liquidacion, Endpoint 1): salen de la
 * {@link Stay}. Módulo 3 ignora cualquier otro parámetro, así que no se envían otros.
 */
public record SettlementRequest(String reservationRef, UUID roomId) {

    public SettlementRequest {
        Objects.requireNonNull(reservationRef, "La referencia de la reserva no puede ser nula");
        Objects.requireNonNull(roomId, "La habitación no puede ser nula");
    }

    public static SettlementRequest of(Stay stay) {
        return new SettlementRequest(stay.getReservationRef(), stay.getRoomId());
    }
}
