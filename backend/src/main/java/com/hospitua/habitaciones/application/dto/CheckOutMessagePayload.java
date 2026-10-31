package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.MovementType;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Contenido del mensaje plano de Check-Out hacia {@code m2.habitacion.checkout.queue} (plan-registrar-check-out.md
 * Contrato 1): la misma estructura que el de Check-In, con {@code DEPARTURE}. {@code messageId} y
 * {@code sequenceNumber} los agrega el Outbox.
 */
public record CheckOutMessagePayload(
        String reservationRef,
        UUID roomId,
        MovementType movementType,
        LocalDate movementDate,
        List<GuestMessageDto> guests) {

    public CheckOutMessagePayload {
        Objects.requireNonNull(reservationRef, "La referencia de la reserva no puede ser nula");
        Objects.requireNonNull(roomId, "La habitación no puede ser nula");
        Objects.requireNonNull(movementDate, "La fecha del movimiento no puede ser nula");
        guests = List.copyOf(guests);
    }

    /** Salida ({@code DEPARTURE}) de todos los que ocuparon la habitación; {@code movementDate} es la del Check-Out. */
    public static CheckOutMessagePayload departure(
            String reservationRef, UUID roomId, LocalDate checkOutDate, List<RoomGuest> guests) {
        return new CheckOutMessagePayload(
                reservationRef,
                roomId,
                MovementType.DEPARTURE,
                checkOutDate,
                guests.stream().map(GuestMessageDto::of).toList());
    }
}
