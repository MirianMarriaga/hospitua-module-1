package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.InvalidCheckInDateException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.StayAlreadyExistsException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Reserva del día, su habitación en la copia local y la habitación física, ya verificadas para el Check-In
 * (plan-registrar-check-in.md T015; FR-003 a FR-005). Las reglas no consultan nada: el Paso 1 las aplica con lo que
 * leyó, y el registro del Check-In las repite con la habitación bloqueada.
 */
public record CheckInEligibility(DailyReservation reservation, DailyReservationRoom reservationRoom, Room room) {

    public CheckInEligibility {
        Objects.requireNonNull(reservation, "La reserva no puede ser nula");
        Objects.requireNonNull(reservationRoom, "La habitación de la reserva no puede ser nula");
        Objects.requireNonNull(room, "La habitación no puede ser nula");
    }

    /**
     * Verifica en orden: que la reserva tenga esa habitación en la copia local (404), que llegue {@code today} (409
     * INVALID_CHECK_IN_DATE), que el par no tenga ya una estancia (409 STAY_ALREADY_EXISTS), que la habitación exista
     * (404) y que esté {@code Reserved} para esta reserva (409 ROOM_INVALID_STATE con el estado actual).
     *
     * @param reservation la reserva de la copia local, o {@code null} si no figura
     * @param room la habitación del inventario, o {@code null} si no existe
     */
    public static CheckInEligibility verify(
            String reservationRef,
            UUID roomId,
            DailyReservation reservation,
            Room room,
            boolean stayExists,
            LocalDate today) {
        DailyReservationRoom reservationRoom = reservation == null
                ? null
                : reservation.rooms().stream()
                        .filter(candidate -> candidate.roomId().equals(roomId))
                        .findFirst()
                        .orElse(null);
        if (reservationRoom == null) {
            throw new ReservationNotFoundException(reservationRef);
        }
        if (!reservation.startDate().equals(today)) {
            throw new InvalidCheckInDateException(reservationRef, reservation.startDate());
        }
        if (stayExists) {
            throw new StayAlreadyExistsException(reservationRoom.roomNumber(), reservationRef);
        }
        if (room == null) {
            throw new RoomNotFoundException();
        }
        boolean reservedForThis =
                room.getStatus() == RoomStatus.Reserved && reservationRef.equals(room.getReservedByReservationRef());
        if (!reservedForThis) {
            String state = room.getStatus() == RoomStatus.Reserved
                    ? "Reservada para otra reserva"
                    : room.getStatus().displayName();
            throw new InvalidRoomTransitionException(
                    room.getStatus(),
                    "La habitación " + room.getRoomNumber() + " está " + state
                            + "; solo se puede hacer el Check-In de una habitación Reservada para esta reserva.");
        }
        return new CheckInEligibility(reservation, reservationRoom, room);
    }
}
