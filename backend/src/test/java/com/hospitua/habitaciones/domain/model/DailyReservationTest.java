package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Copia local de una reserva del día (plan-consultar-reservas.md T006). */
class DailyReservationTest {

    @Test
    @DisplayName("las noches se calculan con endDate - startDate, sin guardarlas")
    void nights_areComputed() {
        assertThat(reservation(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 12), List.of())
                        .nights())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("las habitaciones quedan en una copia inmutable")
    void rooms_areCopied() {
        List<DailyReservationRoom> rooms = new ArrayList<>();
        rooms.add(new DailyReservationRoom(UUID.randomUUID(), "201", "DOBLE", 2));
        DailyReservation reservation = reservation(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10), rooms);

        rooms.clear();

        assertThat(reservation.rooms()).hasSize(1);
        assertThatThrownBy(() -> reservation.rooms().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("la referencia y las fechas son obligatorias")
    void requiredFields() {
        assertThatThrownBy(() -> new DailyReservationRoom(null, "201", "DOBLE", 2))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> reservation(null, LocalDate.of(2026, 10, 10), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("el tipo de mensaje se guarda y se lee por su routing key")
    void messageType_roundTripsByRoutingKey() {
        for (DailyReservationMessageType type : DailyReservationMessageType.values()) {
            assertThat(DailyReservationMessageType.fromRoutingKey(type.routingKey()))
                    .isEqualTo(type);
        }
        assertThat(DailyReservationMessageType.DAILY_LIST.routingKey()).isEqualTo("reserva.lista-del-dia");
        assertThatThrownBy(() -> DailyReservationMessageType.fromRoutingKey("otra"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static DailyReservation reservation(
            LocalDate startDate, LocalDate endDate, List<DailyReservationRoom> rooms) {
        return new DailyReservation(
                "RSV-3F9A1C7B",
                "Ana",
                "Pérez",
                "CC",
                "123456",
                "Colombia",
                "BOOKING",
                startDate,
                endDate,
                3,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                rooms);
    }
}
