package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Registro de un mensaje aplicado o descartado de la cola diaria: base de la idempotencia por {@code messageId} y del
 * orden por {@code sequenceNumber} dentro de cada {@code operationalDate}. {@code reservationRef} es nulo en la lista
 * del día.
 */
public record DailyReservationMessageLog(
        String messageId,
        long sequenceNumber,
        LocalDate operationalDate,
        DailyReservationMessageType messageType,
        String reservationRef,
        OffsetDateTime receivedAt) {

    public DailyReservationMessageLog {
        Objects.requireNonNull(messageId, "El id del mensaje no puede ser nulo");
        Objects.requireNonNull(operationalDate, "El día operativo no puede ser nulo");
        Objects.requireNonNull(messageType, "El tipo de mensaje no puede ser nulo");
        Objects.requireNonNull(receivedAt, "La fecha de recepción no puede ser nula");
    }
}
