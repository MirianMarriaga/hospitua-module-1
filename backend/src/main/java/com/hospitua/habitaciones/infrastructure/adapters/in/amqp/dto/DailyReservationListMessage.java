package com.hospitua.habitaciones.infrastructure.adapters.in.amqp.dto;

import static com.hospitua.habitaciones.infrastructure.adapters.in.amqp.InvalidDailyReservationMessageException.required;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import java.time.LocalDate;
import java.util.List;

/** Mensaje {@code reserva.lista-del-dia} de Módulo 2 (plan-consultar-reservas.md, "Lista del día"). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyReservationListMessage(
        String messageId, Long sequenceNumber, LocalDate operationalDate, List<ReservationPayload> reservations) {

    public DailyReservationListMessageDto toDto() {
        return new DailyReservationListMessageDto(
                required(messageId, "messageId"),
                required(sequenceNumber, "sequenceNumber"),
                required(operationalDate, "operationalDate"),
                required(reservations, "reservations").stream()
                        .map(ReservationPayload::toDomain)
                        .toList());
    }
}
