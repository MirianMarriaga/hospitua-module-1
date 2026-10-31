package com.hospitua.habitaciones.application.dto;

import com.hospitua.habitaciones.domain.model.CheckOutEligibility;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.Stay;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta de {@code GET /api/check-out/active-stay} (plan-registrar-check-out.md Endpoint 1). Los huéspedes llevan
 * solo lo que muestra el Paso 1: sin fecha de nacimiento ni lugares de procedencia y destino.
 */
public record ActiveStayResponseDto(
        UUID stayId,
        String reservationRef,
        UUID roomId,
        String roomNumber,
        RoomCategory categoryRoom,
        RoomStatus roomStatus,
        String titularFirstName,
        String titularLastName,
        LocalDate checkInDate,
        LocalDate expectedCheckoutDate,
        String source,
        List<GuestDto> guests) {

    /** El titular, si se aloja, va primero; los demás, por apellido y nombre. */
    private static final Comparator<RoomGuest> GUEST_ORDER = Comparator.comparing(RoomGuest::isReservationGuest)
            .reversed()
            .thenComparing(RoomGuest::getLastName)
            .thenComparing(RoomGuest::getFirstName);

    public static ActiveStayResponseDto of(CheckOutEligibility eligibility, List<RoomGuest> guests) {
        Stay stay = eligibility.stay();
        Room room = eligibility.room();
        return new ActiveStayResponseDto(
                stay.getId(),
                stay.getReservationRef(),
                room.getId(),
                room.getRoomNumber(),
                room.getCategoryRoom(),
                room.getStatus(),
                stay.getTitularFirstName(),
                stay.getTitularLastName(),
                stay.getCheckInDate(),
                stay.getExpectedCheckoutDate(),
                stay.getSource(),
                guests.stream().sorted(GUEST_ORDER).map(GuestDto::of).toList());
    }

    public record GuestDto(
            String firstName, String lastName, String documentType, String documentNumber, String nationality) {

        static GuestDto of(RoomGuest guest) {
            return new GuestDto(
                    guest.getFirstName(),
                    guest.getLastName(),
                    guest.getDocumentType(),
                    guest.getDocumentNumber(),
                    guest.getNationality());
        }
    }
}
