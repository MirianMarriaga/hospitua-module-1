package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import java.util.Objects;
import java.util.UUID;

/**
 * Comando para marcar una habitación como reservada ante un evento de reserva.
 */
public record MarkRoomReservedCommand(UUID roomId, String reservationRef, ReservationTrigger trigger) {

    public MarkRoomReservedCommand {
        if (roomId == null) {
            throw new IllegalArgumentException("El id de habitación no puede ser nulo");
        }
        if (reservationRef == null || reservationRef.isBlank()) {
            throw new IllegalArgumentException("La referencia de reserva no puede ser nula ni vacía");
        }
        Objects.requireNonNull(trigger, "El disparador no puede ser nulo");
    }
}
