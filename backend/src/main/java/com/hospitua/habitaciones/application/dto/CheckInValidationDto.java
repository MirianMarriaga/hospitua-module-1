package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.CheckInEligibility;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta del Paso 1 del Check-In ({@code GET /api/check-in/validate}, plan-registrar-check-in.md Endpoint 1): la
 * reserva, la habitación con su aforo y el titular en solo lectura (FR-007). {@code nights} es {@code endDate −
 * startDate}.
 */
public record CheckInValidationDto(
        String reservationRef,
        UUID roomId,
        String roomNumber,
        String categoryRoom,
        int guestCount,
        int maxCapacity,
        LocalDate startDate,
        LocalDate endDate,
        long nights,
        String source,
        Holder holder) {

    /** Titular de la reserva, tal como está en la copia local. */
    public record Holder(
            String firstName, String lastName, String documentType, String documentNumber, String nationality) {}

    public static CheckInValidationDto of(CheckInEligibility eligibility) {
        DailyReservation reservation = eligibility.reservation();
        return new CheckInValidationDto(
                reservation.reservationRef(),
                eligibility.room().getId(),
                eligibility.room().getRoomNumber(),
                eligibility.room().getCategoryRoom().name(),
                eligibility.reservationRoom().guestCount(),
                eligibility.room().getMaxCapacity(),
                reservation.startDate(),
                reservation.endDate(),
                reservation.nights(),
                reservation.source(),
                new Holder(
                        reservation.guestFirstName(),
                        reservation.guestLastName(),
                        reservation.guestDocumentType(),
                        reservation.guestDocumentNumber(),
                        reservation.guestNationality()));
    }
}
