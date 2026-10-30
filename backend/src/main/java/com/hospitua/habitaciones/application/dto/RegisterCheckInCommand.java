package com.hospitua.habitaciones.application.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Confirmación del Check-In (plan-registrar-check-in.md, Endpoint 2): los ocupantes reales tal como llegan, y el
 * recepcionista, que es el usuario actual (regla 14 del plan base). Los datos de cada huésped los valida el dominio.
 */
public record RegisterCheckInCommand(
        String reservationRef, UUID roomId, List<GuestInput> guests, String receptionistId) {

    public RegisterCheckInCommand {
        guests = guests == null ? List.of() : List.copyOf(guests);
    }

    /** Un ocupante; {@code reservationGuest} marca al titular si se aloja (FR-007). */
    public record GuestInput(
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            String nationality,
            LocalDate birthDate,
            String originPlace,
            String destinationPlace,
            boolean reservationGuest) {}
}
