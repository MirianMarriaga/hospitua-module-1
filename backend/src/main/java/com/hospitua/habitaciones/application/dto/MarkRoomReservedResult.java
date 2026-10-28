package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.MarkRoomReservedOutcome;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.util.Objects;

/**
 * Resultado detallado de la operación de marcado de habitación como reservada.
 */
public record MarkRoomReservedResult(
        MarkRoomReservedOutcome outcome, RoomStatus currentStatus, String linkedReservationRef) {

    public MarkRoomReservedResult {
        Objects.requireNonNull(outcome, "El resultado de la operación no puede ser nulo");
    }
}
