package com.hospitua.habitaciones.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Códigos y categorías de los errores del Check-In (plan-registrar-check-in.md T007, "Errores de los dos endpoints"). */
class CheckInExceptionsTest {

    @Test
    @DisplayName("T007: cada excepción lleva su errorCode y su categoría (400, 404 o 409)")
    void codesAndCategories() {
        assertError(new ReservationNotFoundException("R"), DomainError.RESERVATION_NOT_FOUND, ErrorCategory.NOT_FOUND);
        assertError(
                new InvalidCheckInDateException("R", LocalDate.of(2026, 10, 10)),
                DomainError.INVALID_CHECK_IN_DATE,
                ErrorCategory.CONFLICT);
        assertError(
                new StayAlreadyExistsException("204", "R"), DomainError.STAY_ALREADY_EXISTS, ErrorCategory.CONFLICT);
        assertError(
                new GuestCountMismatchException(2, 3), DomainError.GUEST_COUNT_MISMATCH, ErrorCategory.INVALID_REQUEST);
        assertError(
                new RoomCapacityExceededException(2, 3),
                DomainError.ROOM_CAPACITY_EXCEEDED,
                ErrorCategory.INVALID_REQUEST);
        assertError(
                new DuplicateGuestDocumentException("CC", "123", 1),
                DomainError.DUPLICATE_GUEST_DOCUMENT,
                ErrorCategory.INVALID_REQUEST);
    }

    @Test
    @DisplayName("T007: los errores de aforo y de documento señalan los números y el huésped repetido")
    void details() {
        assertThat(new GuestCountMismatchException(2, 3).details())
                .isEqualTo(Map.of("expectedGuests", 2, "receivedGuests", 3));
        assertThat(new RoomCapacityExceededException(2, 3).details())
                .isEqualTo(Map.of("maxCapacity", 2, "receivedGuests", 3));
        assertThat(new DuplicateGuestDocumentException("CC", "123", 1).details())
                .isEqualTo(Map.of("guestIndex", 1));
    }

    private static void assertError(DomainException ex, DomainError error, ErrorCategory category) {
        assertThat(ex.error()).isEqualTo(error);
        assertThat(ex.error().category()).isEqualTo(category);
        assertThat(ex.getMessage()).isNotBlank();
    }
}
