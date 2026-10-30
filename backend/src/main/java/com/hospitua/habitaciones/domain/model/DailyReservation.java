package com.hospitua.habitaciones.domain.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Reserva de la copia local del día (plan-consultar-reservas.md). La recibe Módulo 2 por la cola diaria; la clave es
 * {@code reservationRef}. No guarda noches: se calculan con {@link #nights()}. {@code updatedAt} es informativo; el
 * orden de los cambios lo decide el {@code sequenceNumber} del mensaje.
 */
public record DailyReservation(
        String reservationRef,
        String guestFirstName,
        String guestLastName,
        String guestDocumentType,
        String guestDocumentNumber,
        String guestNationality,
        String source,
        LocalDate startDate,
        LocalDate endDate,
        int guestCount,
        OffsetDateTime updatedAt,
        List<DailyReservationRoom> rooms) {

    public DailyReservation {
        Objects.requireNonNull(reservationRef, "La referencia de la reserva no puede ser nula");
        Objects.requireNonNull(guestFirstName, "El nombre del titular no puede ser nulo");
        Objects.requireNonNull(guestLastName, "El apellido del titular no puede ser nulo");
        Objects.requireNonNull(guestDocumentType, "El tipo de documento del titular no puede ser nulo");
        Objects.requireNonNull(guestDocumentNumber, "El documento del titular no puede ser nulo");
        Objects.requireNonNull(guestNationality, "La nacionalidad del titular no puede ser nula");
        Objects.requireNonNull(source, "El origen de la reserva no puede ser nulo");
        Objects.requireNonNull(startDate, "La fecha de llegada no puede ser nula");
        Objects.requireNonNull(endDate, "La fecha de salida no puede ser nula");
        Objects.requireNonNull(updatedAt, "La fecha de actualización no puede ser nula");
        rooms = List.copyOf(rooms);
    }

    /** Noches de la reserva ({@code endDate - startDate}), calculadas sin persistirlas. */
    public long nights() {
        return ChronoUnit.DAYS.between(startDate, endDate);
    }
}
