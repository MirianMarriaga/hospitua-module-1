package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.DuplicateGuestDocumentException;
import com.hospitua.habitaciones.domain.exception.GuestCountMismatchException;
import com.hospitua.habitaciones.domain.exception.InvalidCheckInDateException;
import com.hospitua.habitaciones.domain.exception.InvalidGuestDataException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.ReservationNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomCapacityExceededException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.StayAlreadyExistsException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CheckInEligibilityTest {

    private static final String REF = "RSV-8D02E5A4";
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final DailyReservationRoom RESERVATION_ROOM = new DailyReservationRoom(ROOM_ID, "204", "DOBLE", 2);

    @Test
    @DisplayName("T008: la reserva de hoy con la habitación Reserved para ella es apta para el Check-In")
    void eligible() {
        Room room = room(RoomStatus.Reserved, REF);

        CheckInEligibility eligibility =
                CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY), room, false, TODAY);

        assertThat(eligibility.reservationRoom()).isEqualTo(RESERVATION_ROOM);
        assertThat(eligibility.room()).isSameAs(room);
    }

    @Test
    @DisplayName("Esc. 7: una reserva fuera de la copia local, o sin esa habitación, es RESERVATION_NOT_FOUND")
    void notInLocalCopy_isNotFound() {
        Room room = room(RoomStatus.Reserved, REF);

        assertThatThrownBy(() -> CheckInEligibility.verify(REF, ROOM_ID, null, room, false, TODAY))
                .isInstanceOf(ReservationNotFoundException.class)
                .hasMessageContaining(REF);
        assertThatThrownBy(
                        () -> CheckInEligibility.verify(REF, UUID.randomUUID(), reservation(TODAY), room, false, TODAY))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    @DisplayName("T026: una reserva que no llega hoy es INVALID_CHECK_IN_DATE, aunque la habitación esté apartada")
    void notArrivingToday_isInvalidDate() {
        Room room = room(RoomStatus.Reserved, REF);

        assertThatThrownBy(() ->
                        CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY.plusDays(1)), room, false, TODAY))
                .isInstanceOf(InvalidCheckInDateException.class)
                .hasMessageContaining("10-10-2026");
        assertThatThrownBy(() ->
                        CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY.minusDays(1)), room, false, TODAY))
                .isInstanceOf(InvalidCheckInDateException.class);
    }

    @Test
    @DisplayName("T020: si el par ya tiene una estancia es STAY_ALREADY_EXISTS, antes de mirar el estado")
    void existingStay_isRejected() {
        Room occupied = room(RoomStatus.Occupied, null);

        assertThatThrownBy(() -> CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY), occupied, true, TODAY))
                .isInstanceOf(StayAlreadyExistsException.class)
                .hasMessageContaining("204")
                .hasMessageContaining(REF);
    }

    @Test
    @DisplayName("Una habitación que no está en el inventario es ROOM_NOT_FOUND")
    void missingRoom_isNotFound() {
        assertThatThrownBy(() -> CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY), null, false, TODAY))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = RoomStatus.class, names = "Reserved", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T019: en cualquier estado distinto de Reserved es ROOM_INVALID_STATE con el estado actual")
    void notReserved_isInvalidState(RoomStatus status) {
        Room room = room(status, null);

        assertThatThrownBy(() -> CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY), room, false, TODAY))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .hasMessage("La habitación 204 está " + status.displayName()
                        + "; solo se puede hacer el Check-In de una habitación Reservada para esta reserva.")
                .satisfies(ex -> assertThat(((InvalidRoomTransitionException) ex).details())
                        .isEqualTo(Map.of("currentStatus", status.name())));
    }

    @Test
    @DisplayName("T019: una habitación Reserved para otra reserva es ROOM_INVALID_STATE")
    void reservedForAnother_isInvalidState() {
        Room room = room(RoomStatus.Reserved, "RSV-OTRA");

        assertThatThrownBy(() -> CheckInEligibility.verify(REF, ROOM_ID, reservation(TODAY), room, false, TODAY))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .hasMessageStartingWith("La habitación 204 está Reservada para otra reserva;");
    }

    @Test
    @DisplayName("T009: los ocupantes que coinciden con el guestCount y caben en la habitación son aceptados")
    void verifyGuests_accepted() {
        CheckInEligibility eligibility = eligibleReservation();

        eligibility.verifyGuests(List.of(guest("CC", "123", true), guest("TI", "456", false)));
        // El titular puede no alojarse: entonces no es ocupante y no cuenta para el aforo
        eligibility.verifyGuests(List.of(guest("CC", "777", false), guest("TI", "456", false)));
    }

    @Test
    @DisplayName("T021: menos o más ocupantes que el guestCount de la reserva es GUEST_COUNT_MISMATCH")
    void verifyGuests_countMismatch() {
        CheckInEligibility eligibility = eligibleReservation();

        assertThatThrownBy(() -> eligibility.verifyGuests(List.of(guest("CC", "123", true))))
                .isInstanceOf(GuestCountMismatchException.class)
                .extracting(e -> ((GuestCountMismatchException) e).details())
                .isEqualTo(Map.of("expectedGuests", 2, "receivedGuests", 1));
        assertThatThrownBy(() -> eligibility.verifyGuests(
                        List.of(guest("CC", "1", false), guest("CC", "2", false), guest("CC", "3", false))))
                .isInstanceOf(GuestCountMismatchException.class);
    }

    @Test
    @DisplayName("T021: más ocupantes que la capacidad máxima es ROOM_CAPACITY_EXCEEDED, antes que el guestCount")
    void verifyGuests_capacityExceeded() {
        CheckInEligibility eligibility = eligibleReservation();

        assertThatThrownBy(() -> eligibility.verifyGuests(List.of(
                        guest("CC", "1", false),
                        guest("CC", "2", false),
                        guest("CC", "3", false),
                        guest("CC", "4", false))))
                .isInstanceOf(RoomCapacityExceededException.class)
                .extracting(e -> ((RoomCapacityExceededException) e).details())
                .isEqualTo(Map.of("maxCapacity", 3, "receivedGuests", 4));
    }

    @Test
    @DisplayName("T022: dos ocupantes con el mismo tipo y número de documento es DUPLICATE_GUEST_DOCUMENT")
    void verifyGuests_duplicateDocument() {
        CheckInEligibility eligibility = eligibleReservation();

        assertThatThrownBy(
                        () -> eligibility.verifyGuests(List.of(guest("PAS", "ab1", false), guest("PAS", "AB1", false))))
                .isInstanceOf(DuplicateGuestDocumentException.class)
                .extracting(e -> ((DuplicateGuestDocumentException) e).details())
                .isEqualTo(Map.of("guestIndex", 1));
        eligibility.verifyGuests(List.of(guest("CC", "777", false), guest("TI", "777", false)));
    }

    @Test
    @DisplayName("T009: el titular marcado debe tener el documento del titular, y solo puede haber uno")
    void verifyGuests_reservationGuest() {
        CheckInEligibility eligibility = eligibleReservation();

        assertThatThrownBy(() -> eligibility.verifyGuests(List.of(guest("CC", "999", true), guest("CC", "1", false))))
                .isInstanceOf(InvalidGuestDataException.class)
                .extracting(e -> ((InvalidGuestDataException) e).details())
                .isEqualTo(Map.of("field", "isReservationGuest", "guestIndex", 0));
        assertThatThrownBy(() -> eligibility.verifyGuests(List.of(guest("CC", "123", true), guest("CC", "123", true))))
                .isInstanceOf(InvalidGuestDataException.class)
                .extracting(e -> ((InvalidGuestDataException) e).details().get("guestIndex"))
                .isEqualTo(1);
    }

    private static CheckInEligibility eligibleReservation() {
        return CheckInEligibility.verify(
                REF, ROOM_ID, reservation(TODAY), room(RoomStatus.Reserved, REF), false, TODAY);
    }

    private static CheckInGuest guest(String documentType, String documentNumber, boolean reservationGuest) {
        return CheckInGuest.of(
                0,
                "Ana",
                "Pérez",
                documentType,
                documentNumber,
                "Colombia",
                LocalDate.of(1990, 5, 20),
                null,
                null,
                reservationGuest,
                TODAY);
    }

    private static DailyReservation reservation(LocalDate startDate) {
        return new DailyReservation(
                REF,
                "Ana",
                "Pérez",
                "CC",
                "123",
                "Colombia",
                "BOOKING",
                startDate,
                startDate.plusDays(2),
                2,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(RESERVATION_ROOM));
    }

    private static Room room(RoomStatus status, String reservedBy) {
        return new Room(ROOM_ID, "204", 2, RoomCategory.DOBLE, 3, new BigDecimal("200000"), status, reservedBy, 0);
    }
}
