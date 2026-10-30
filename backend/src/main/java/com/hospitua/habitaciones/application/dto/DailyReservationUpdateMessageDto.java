package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationUpdateType;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Actualización del día ({@code reserva.lista-del-dia.actualizacion}), ya validada por el listener. {@code reservation}
 * es nula en un {@code REMOVED}.
 */
public record DailyReservationUpdateMessageDto(
        String messageId,
        long sequenceNumber,
        LocalDate operationalDate,
        DailyReservationUpdateType updateType,
        String reservationRef,
        DailyReservation reservation) {

    public DailyReservationUpdateMessageDto {
        Objects.requireNonNull(messageId, "El id del mensaje no puede ser nulo");
        Objects.requireNonNull(operationalDate, "El día operativo no puede ser nulo");
        Objects.requireNonNull(updateType, "El tipo de actualización no puede ser nulo");
        Objects.requireNonNull(reservationRef, "La referencia de la reserva no puede ser nula");
        if (updateType != DailyReservationUpdateType.REMOVED && reservation == null) {
            throw new IllegalArgumentException("Un " + updateType + " debe traer la reserva completa");
        }
    }
}
