package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.CheckInGuest;
import com.hospitua.habitaciones.domain.model.MovementType;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Contenido del mensaje plano de Check-In hacia {@code m2.habitacion.checkin.queue} (plan-registrar-check-in.md
 * Contrato 1, T006). {@code messageId} y {@code sequenceNumber} los agrega el Outbox (T012); los lugares de un
 * huésped colombiano son nulos y no viajan.
 */
public record CheckInMessagePayload(
        String reservationRef,
        UUID roomId,
        MovementType movementType,
        LocalDate movementDate,
        List<GuestMessageDto> guests) {

    public CheckInMessagePayload {
        Objects.requireNonNull(reservationRef, "La referencia de la reserva no puede ser nula");
        Objects.requireNonNull(roomId, "La habitación no puede ser nula");
        Objects.requireNonNull(movementDate, "La fecha del movimiento no puede ser nula");
        guests = List.copyOf(guests);
    }

    /** Ingreso ({@code ENTRY}) con todos los ocupantes; {@code movementDate} es la fecha del Check-In. */
    public static CheckInMessagePayload entry(
            String reservationRef, UUID roomId, LocalDate checkInDate, List<CheckInGuest> guests) {
        return new CheckInMessagePayload(
                reservationRef,
                roomId,
                MovementType.ENTRY,
                checkInDate,
                guests.stream().map(GuestMessageDto::of).toList());
    }

    /** Huésped del mensaje (Contrato 1, campos de {@code guests[]}). */
    public record GuestMessageDto(
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            LocalDate birthDate,
            String nationality,
            String originPlace,
            String destinationPlace) {

        static GuestMessageDto of(CheckInGuest guest) {
            return new GuestMessageDto(
                    guest.firstName(),
                    guest.lastName(),
                    guest.documentType(),
                    guest.documentNumber(),
                    guest.birthDate(),
                    guest.nationality(),
                    guest.originPlace(),
                    guest.destinationPlace());
        }
    }
}
