package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.model.ReservationConflictResult.ConflictingReservation;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservationConflictQueryTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("T022: acepta un rango de un día y uno con fin posterior al inicio")
    void acceptsValidRanges() {
        assertThat(new ReservationConflictQuery(ROOM_ID, START, START).endDate())
                .isEqualTo(START);
        assertThat(new ReservationConflictQuery(ROOM_ID, START, START.plusDays(2)).endDate())
                .isEqualTo(START.plusDays(2));
    }

    @Test
    @DisplayName("T022: un fin anterior al inicio o un dato nulo es un error de programación")
    void rejectsInvertedRangeAndNulls() {
        assertThatThrownBy(() -> new ReservationConflictQuery(ROOM_ID, START, START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReservationConflictQuery(null, START, START))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReservationConflictQuery(ROOM_ID, null, START))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReservationConflictQuery(ROOM_ID, START, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("T022: una reserva en conflicto sin referencia, estado o fechas es un error de programación")
    void conflictingReservationRejectsNulls() {
        LocalDate end = START.plusDays(3);
        assertThatThrownBy(() -> new ConflictingReservation(null, "ACTIVE", START, end))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConflictingReservation("RSV-1", null, START, end))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConflictingReservation("RSV-1", "ACTIVE", null, end))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ConflictingReservation("RSV-1", "ACTIVE", START, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("T022: sin reservas no hay conflicto; con alguna, sí, y la lista no cambia después")
    void resultHasConflictOnlyWithReservations() {
        List<ConflictingReservation> reservations = new ArrayList<>();
        assertThat(new ReservationConflictResult(reservations).hasConflict()).isFalse();

        reservations.add(new ConflictingReservation("RSV-1", "ACTIVE", START, START.plusDays(3)));
        ReservationConflictResult result = new ReservationConflictResult(reservations);
        reservations.clear();

        assertThat(result.hasConflict()).isTrue();
        assertThat(result.reservations()).hasSize(1);
    }
}
