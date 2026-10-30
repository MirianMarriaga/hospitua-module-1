package com.hospitua.habitaciones.domain.exception;

import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La habitación tiene reservas en el rango consultado (409 RESERVATION_CONFLICT, con {@code details.reservations}).
 * La comparten la baja y el bloqueo técnico; cada uno pasa su mensaje (consultar-reservas T029).
 */
public final class ReservationConflictException extends DomainException {

    public ReservationConflictException(String message, List<ConflictingReservation> reservations) {
        super(DomainError.RESERVATION_CONFLICT, message, Map.of("reservations", toDetails(reservations)));
    }

    private static List<Map<String, Object>> toDetails(List<ConflictingReservation> reservations) {
        return reservations.stream().map(ReservationConflictException::toDetail).toList();
    }

    private static Map<String, Object> toDetail(ConflictingReservation reservation) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reservationRef", reservation.reservationRef());
        detail.put("startDate", String.valueOf(reservation.startDate()));
        detail.put("endDate", String.valueOf(reservation.endDate()));
        detail.put("status", reservation.status());
        return detail;
    }
}
