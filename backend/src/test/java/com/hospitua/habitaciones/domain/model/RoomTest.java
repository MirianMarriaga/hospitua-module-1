package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RoomTest {

    private static final Map<RoomStatus, Set<RoomStatus>> EXPECTED_ALLOWED = Map.of(
            RoomStatus.Available,
            Set.of(
                    RoomStatus.Reserved,
                    RoomStatus.InCleaning,
                    RoomStatus.DisabledForRepairs,
                    RoomStatus.TechnicalBlock,
                    RoomStatus.Inactive),
            RoomStatus.Reserved,
            Set.of(RoomStatus.Available, RoomStatus.Occupied),
            RoomStatus.Occupied,
            Set.of(RoomStatus.PendingCleaning),
            RoomStatus.PendingCleaning,
            Set.of(RoomStatus.InCleaning),
            RoomStatus.InCleaning,
            Set.of(RoomStatus.Available, RoomStatus.PendingCleaning),
            RoomStatus.DisabledForRepairs,
            Set.of(RoomStatus.PendingCleaning),
            RoomStatus.TechnicalBlock,
            Set.of(RoomStatus.PendingCleaning),
            RoomStatus.Inactive,
            Set.of(RoomStatus.Available));

    @Test
    @DisplayName("Debe evaluar exhaustivamente las 64 combinaciones (8x8) de la matriz de estados")
    void shouldEvaluateAll64StateTransitionsExhaustively() {
        int evaluatedCombinations = 0;
        int allowedCount = 0;
        int forbiddenCount = 0;

        for (RoomStatus from : RoomStatus.values()) {
            Set<RoomStatus> allowedForFrom = EXPECTED_ALLOWED.getOrDefault(from, Set.of());

            for (RoomStatus to : RoomStatus.values()) {
                evaluatedCombinations++;
                Room room = createRoomWithStatus(from);

                if (allowedForFrom.contains(to)) {
                    allowedCount++;
                    room.transitionTo(to);
                    assertThat(room.getStatus()).isEqualTo(to);
                } else {
                    forbiddenCount++;
                    assertThatThrownBy(() -> room.transitionTo(to))
                            .isInstanceOf(InvalidRoomTransitionException.class)
                            .satisfies(ex -> {
                                var ite = (InvalidRoomTransitionException) ex;
                                assertThat(ite.getCurrentStatus()).isEqualTo(from);
                            });
                }
            }
        }

        assertThat(evaluatedCombinations).isEqualTo(64);
        assertThat(allowedCount).isEqualTo(14);
        assertThat(forbiddenCount).isEqualTo(50);
    }

    @Test
    @DisplayName("reserveFor debe transicionar a Reserved y asignar reservedByReservationRef")
    void shouldTransitionToReservedAndAssignRef() {
        Room room = createRoomWithStatus(RoomStatus.Available);

        room.reserveFor("RES-12345");

        assertThat(room.getStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(room.getReservedByReservationRef()).isEqualTo("RES-12345");
    }

    @ParameterizedTest
    @CsvSource(
            value = {"''", "'   '", "null"},
            nullValues = {"null"})
    @DisplayName("reserveFor debe rechazar referencia nula o vacía")
    void reserveForShouldRejectBlankOrNullReference(String invalidRef) {
        Room room = createRoomWithStatus(RoomStatus.Available);

        assertThatThrownBy(() -> room.reserveFor(invalidRef)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transitionTo debe limpiar reservedByReservationRef si el destino no es Reserved")
    void transitionToShouldClearReservationRefWhenTargetIsNotReserved() {
        Room room = createRoomWithStatus(RoomStatus.Available);
        room.reserveFor("RES-777");
        assertThat(room.getReservedByReservationRef()).isEqualTo("RES-777");

        // Salida de Reserved a Available
        room.transitionTo(RoomStatus.Available);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(room.getReservedByReservationRef()).isNull();

        // Reservar nuevamente y pasar a Occupied (Check-in)
        room.reserveFor("RES-888");
        room.transitionTo(RoomStatus.Occupied);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.Occupied);
        assertThat(room.getReservedByReservationRef()).isNull();
    }

    private Room createRoomWithStatus(RoomStatus status) {
        return new Room(
                UUID.randomUUID(),
                "101",
                1,
                RoomCategory.SENCILLA,
                2,
                new BigDecimal("120000.00"),
                status,
                status == RoomStatus.Reserved ? "RES-INIT" : null,
                0);
    }
}
