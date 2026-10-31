package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.ActiveStayNotFoundException;
import com.hospitua.habitaciones.domain.exception.DomainException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CheckOutEligibilityTest {

    private static final String REF = "RSV-8D02E5A4";
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 9);

    @Test
    @DisplayName("Una estancia activa en una habitación Ocupada es elegible para el Check-Out")
    void activeStayInOccupiedRoom_isEligible() {
        Stay stay = stay(null);
        Room room = room(RoomStatus.Occupied);

        CheckOutEligibility eligibility = CheckOutEligibility.verify(REF, ROOM_ID, stay, room);

        assertThat(eligibility.stay()).isSameAs(stay);
        assertThat(eligibility.room()).isSameAs(room);
    }

    @Test
    @DisplayName("Sin estancia o con la estancia cerrada responde ACTIVE_STAY_NOT_FOUND con el par en details")
    void missingOrClosedStay_isNotFound() {
        for (Stay stay : new Stay[] {null, stay(CHECK_IN.plusDays(2))}) {
            assertThatThrownBy(() -> CheckOutEligibility.verify(REF, ROOM_ID, stay, room(RoomStatus.Occupied)))
                    .isInstanceOf(ActiveStayNotFoundException.class)
                    .hasMessage("No hay una estancia activa de la reserva " + REF + " en esta habitación.")
                    .extracting(ex -> ((DomainException) ex).details())
                    .isEqualTo(Map.of("reservationRef", REF, "roomId", ROOM_ID.toString()));
        }
    }

    @Test
    @DisplayName("Sin la habitación en el inventario responde ROOM_NOT_FOUND")
    void missingRoom_isRoomNotFound() {
        assertThatThrownBy(() -> CheckOutEligibility.verify(REF, ROOM_ID, stay(null), null))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("FR-004: una habitación que no está Ocupada responde ROOM_INVALID_STATE con su estado actual")
    void roomNotOccupied_isInvalidState() {
        assertThatThrownBy(() -> CheckOutEligibility.verify(REF, ROOM_ID, stay(null), room(RoomStatus.PendingCleaning)))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .hasMessage("La habitación 204 está " + RoomStatus.PendingCleaning.displayName()
                        + "; solo se puede hacer el Check-Out de una habitación Ocupada.")
                .extracting(ex -> ((InvalidRoomTransitionException) ex).getCurrentStatus())
                .isEqualTo(RoomStatus.PendingCleaning);
    }

    private static Stay stay(LocalDate checkOutDate) {
        return new Stay(
                UUID.randomUUID(),
                REF,
                ROOM_ID,
                "BOOKING",
                CHECK_IN,
                checkOutDate,
                CHECK_IN,
                CHECK_IN.plusDays(2),
                "Ana",
                "Pérez",
                "123",
                0);
    }

    private static Room room(RoomStatus status) {
        return new Room(ROOM_ID, "204", 2, RoomCategory.DOBLE, 3, new BigDecimal("200000"), status, null, 0);
    }
}
