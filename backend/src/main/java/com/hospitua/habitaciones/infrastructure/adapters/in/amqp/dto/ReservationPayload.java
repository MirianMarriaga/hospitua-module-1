package com.hospitua.habitaciones.infrastructure.adapters.in.amqp.dto;

import static com.hospitua.habitaciones.infrastructure.adapters.in.amqp.InvalidDailyReservationMessageException.required;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.infrastructure.adapters.in.amqp.InvalidDailyReservationMessageException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reserva de la lista del día o de un ADDED/UPDATED, tal como la envía Módulo 2. {@code status}, {@code notes} y
 * {@code guestRef} no se guardan (FR-004).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservationPayload(
        String reservationRef,
        String source,
        LocalDate startDate,
        LocalDate endDate,
        Integer guestCount,
        OffsetDateTime updatedAt,
        List<RoomPayload> rooms,
        GuestPayload guest) {

    private static final int MAX_ROOMS = 10;

    public DailyReservation toDomain() {
        List<RoomPayload> assigned = required(rooms, "rooms");
        if (assigned.isEmpty() || assigned.size() > MAX_ROOMS) {
            throw new InvalidDailyReservationMessageException(
                    "La reserva " + reservationRef + " debe traer de 1 a 10 habitaciones.");
        }
        GuestPayload titular = required(guest, "guest");
        return new DailyReservation(
                required(reservationRef, "reservationRef"),
                required(titular.firstName(), "guest.firstName"),
                required(titular.lastName(), "guest.lastName"),
                required(titular.documentType(), "guest.documentType"),
                required(titular.documentNumber(), "guest.documentNumber"),
                required(titular.nationality(), "guest.nationality"),
                required(source, "source"),
                required(startDate, "startDate"),
                required(endDate, "endDate"),
                required(guestCount, "guestCount"),
                required(updatedAt, "updatedAt"),
                assigned.stream().map(RoomPayload::toDomain).toList());
    }

    /** Habitación asignada a la reserva. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoomPayload(UUID roomId, String roomNumber, String categoryRoom, Integer guestCount) {

        DailyReservationRoom toDomain() {
            return new DailyReservationRoom(
                    required(roomId, "rooms.roomId"),
                    required(roomNumber, "rooms.roomNumber"),
                    required(categoryRoom, "rooms.categoryRoom"),
                    required(guestCount, "rooms.guestCount"));
        }
    }

    /** Titular de la reserva. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GuestPayload(
            String firstName, String lastName, String documentType, String documentNumber, String nationality) {}
}
