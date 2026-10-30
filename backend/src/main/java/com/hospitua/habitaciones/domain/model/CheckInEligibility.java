package com.hospitua.habitaciones.domain.model;

import com.hospitua.habitaciones.domain.exception.DuplicateGuestDocumentException;
import com.hospitua.habitaciones.domain.exception.GuestCountMismatchException;
import com.hospitua.habitaciones.domain.exception.InvalidCheckInDateException;
import com.hospitua.habitaciones.domain.exception.InvalidGuestDataException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomCapacityExceededException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.StayAlreadyExistsException;
import java.time.LocalDate;
import java.util.List;
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

    /**
     * Verifica los ocupantes reales contra la reserva y la habitación (FR-007, FR-008), en orden: como mucho uno marcado
     * como titular y con el documento del titular de la copia local, no más que {@code maxCapacity} (400
     * ROOM_CAPACITY_EXCEEDED), exactamente el {@code guestCount} de la habitación (400 GUEST_COUNT_MISMATCH) y sin
     * documentos repetidos (400 DUPLICATE_GUEST_DOCUMENT).
     */
    public void verifyGuests(List<CheckInGuest> guests) {
        boolean holderSeen = false;
        for (int i = 0; i < guests.size(); i++) {
            CheckInGuest guest = guests.get(i);
            if (!guest.reservationGuest()) {
                continue;
            }
            if (holderSeen) {
                throw new InvalidGuestDataException(
                        i, "isReservationGuest", "Solo un huésped puede ser el titular de la reserva.");
            }
            if (!isHolder(guest)) {
                throw new InvalidGuestDataException(
                        i, "isReservationGuest", "El huésped marcado como titular no tiene el documento del titular.");
            }
            holderSeen = true;
        }
        if (guests.size() > room.getMaxCapacity()) {
            throw new RoomCapacityExceededException(room.getMaxCapacity(), guests.size());
        }
        if (guests.size() != reservationRoom.guestCount()) {
            throw new GuestCountMismatchException(reservationRoom.guestCount(), guests.size());
        }
        for (int i = 1; i < guests.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (guests.get(i).sameDocumentAs(guests.get(j))) {
                    throw new DuplicateGuestDocumentException(
                            guests.get(i).documentType(), guests.get(i).documentNumber(), i);
                }
            }
        }
    }

    private boolean isHolder(CheckInGuest guest) {
        return guest.documentType().equalsIgnoreCase(reservation.guestDocumentType())
                && guest.documentNumber().equalsIgnoreCase(reservation.guestDocumentNumber());
    }
}
