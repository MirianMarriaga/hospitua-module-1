package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DepartureSummaryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);

    @Test
    @DisplayName("FR-007: la salida es vencida solo si la salida esperada es anterior a hoy")
    void overdueOnlyBeforeToday() {
        assertThat(summary(TODAY.minusDays(1)).overdue()).isTrue();
        assertThat(summary(TODAY).overdue()).isFalse();
    }

    @Test
    @DisplayName("FR-004: la fila copia la estancia, la habitación y el conteo de huéspedes")
    void copiesStayRoomAndGuests() {
        DepartureSummary summary = summary(TODAY);

        assertThat(summary.reservationRef()).isEqualTo("RES-10411");
        assertThat(summary.roomNumber()).isEqualTo("210");
        assertThat(summary.categoryRoom()).isEqualTo("SUITE");
        assertThat(summary.titularLastName()).isEqualTo("Pérez");
        assertThat(summary.checkInDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(summary.guestCount()).isEqualTo(2);
        assertThat(summary.source()).isEqualTo("DIRECTA");
    }

    private static DepartureSummary summary(LocalDate expectedCheckout) {
        Stay stay = new Stay(
                UUID.randomUUID(),
                "RES-10411",
                UUID.randomUUID(),
                "DIRECTA",
                TODAY.minusDays(2),
                null,
                TODAY.minusDays(2),
                expectedCheckout,
                "Carlos",
                "Pérez",
                "79123456",
                0);
        return DepartureSummary.of(stay, "210", RoomCategory.SUITE, 2, TODAY);
    }
}
