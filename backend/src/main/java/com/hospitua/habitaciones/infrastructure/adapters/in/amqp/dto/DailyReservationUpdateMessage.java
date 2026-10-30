package com.hospitua.habitaciones.infrastructure.adapters.in.amqp.dto;

import static com.hospitua.habitaciones.infrastructure.adapters.in.amqp.InvalidDailyReservationMessageException.required;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hospitua.habitaciones.application.dto.DailyReservationUpdateMessageDto;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationUpdateType;
import com.hospitua.habitaciones.infrastructure.adapters.in.amqp.InvalidDailyReservationMessageException;
import java.time.LocalDate;

/**
 * Mensaje {@code reserva.lista-del-dia.actualizacion} de Módulo 2: {@code ADDED} y {@code UPDATED} traen la reserva
 * completa; {@code REMOVED} solo su referencia y el motivo, que no se guarda.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DailyReservationUpdateMessage(
        String messageId,
        Long sequenceNumber,
        LocalDate operationalDate,
        String updateType,
        String reservationRef,
        ReservationPayload reservation) {

    public DailyReservationUpdateMessageDto toDto() {
        DailyReservationUpdateType type = type();
        String ref = required(reservationRef, "reservationRef");
        DailyReservation full = null;
        if (type != DailyReservationUpdateType.REMOVED) {
            full = required(reservation, "reservation").toDomain();
            if (!full.reservationRef().equals(ref)) {
                throw new InvalidDailyReservationMessageException(
                        "La reserva " + full.reservationRef() + " no coincide con reservationRef " + ref + ".");
            }
        }
        return new DailyReservationUpdateMessageDto(
                required(messageId, "messageId"),
                required(sequenceNumber, "sequenceNumber"),
                required(operationalDate, "operationalDate"),
                type,
                ref,
                full);
    }

    private DailyReservationUpdateType type() {
        try {
            return DailyReservationUpdateType.valueOf(required(updateType, "updateType"));
        } catch (IllegalArgumentException ex) {
            throw new InvalidDailyReservationMessageException("updateType desconocido: " + updateType + ".", ex);
        }
    }
}
