package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.DomainError;
import com.hospitua.habitaciones.domain.exception.InvalidRoomDataException;
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

    @Test
    @DisplayName("register crea la habitación en Available con UUID propio, versión 0 y el número sin espacios")
    void register_createsAvailableRoom() {
        Room room = Room.register("  407 ", 4, RoomCategory.DOBLE, 2, new BigDecimal("250000"));

        assertThat(room.getId()).isNotNull();
        assertThat(room.getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(room.getRoomNumber()).isEqualTo("407");
        assertThat(room.getReservedByReservationRef()).isNull();
        assertThat(room.getVersion()).isZero();
    }

    @ParameterizedTest(name = "{0} = {1} se rechaza")
    @CsvSource(
            nullValues = "null",
            value = {
                "roomNumber, ' '",
                "floor, 0",
                "floor, -1",
                "floor, null",
                "categoryRoom, null",
                "maxCapacity, 0",
                "maxCapacity, -2",
                "baseRate, 0",
                "baseRate, -10",
                "baseRate, 100.123"
            })
    @DisplayName("register rechaza los atributos inválidos con VALIDATION_ERROR y el campo")
    void register_rejectsInvalidAttribute(String field, String value) {
        String roomNumber = field.equals("roomNumber") ? value : "407";
        Integer floor = field.equals("floor") ? (value == null ? null : Integer.valueOf(value)) : Integer.valueOf(4);
        RoomCategory category = field.equals("categoryRoom") ? null : RoomCategory.DOBLE;
        Integer capacity = field.equals("maxCapacity") ? Integer.valueOf(value) : 2;
        BigDecimal rate = field.equals("baseRate") ? new BigDecimal(value) : new BigDecimal("250000");

        assertThatThrownBy(() -> Room.register(roomNumber, floor, category, capacity, rate))
                .isInstanceOf(InvalidRoomDataException.class)
                .satisfies(ex -> {
                    InvalidRoomDataException invalid = (InvalidRoomDataException) ex;
                    assertThat(invalid.error()).isEqualTo(DomainError.VALIDATION_ERROR);
                    assertThat(invalid.details()).containsEntry("field", field);
                });
    }

    @Test
    @DisplayName("register acepta una tarifa con ceros decimales de sobra (250000.000)")
    void register_acceptsTrailingZeros() {
        Room room = Room.register("407", 4, RoomCategory.DOBLE, 2, new BigDecimal("250000.000"));

        assertThat(room.getBaseRate()).isEqualByComparingTo("250000");
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
