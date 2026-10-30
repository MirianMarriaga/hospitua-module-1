package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.DailyReservation;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Lista del día de las 00:00 ({@code reserva.lista-del-dia}), ya validada por el listener. */
public record DailyReservationListMessageDto(
        String messageId, long sequenceNumber, LocalDate operationalDate, List<DailyReservation> reservations) {

    public DailyReservationListMessageDto {
        Objects.requireNonNull(messageId, "El id del mensaje no puede ser nulo");
        Objects.requireNonNull(operationalDate, "El día operativo no puede ser nulo");
        reservations = List.copyOf(reservations);
    }
}
