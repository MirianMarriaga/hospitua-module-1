package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StayTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 9);

    @Test
    @DisplayName("checkOut cierra la estancia en la fecha dada y conserva sus datos y su version")
    void checkOut_closesStayKeepingDataAndVersion() {
        Stay active = stay(null);

        Stay closed = active.checkOut(CHECK_IN.plusDays(2));

        assertThat(active.isActive()).isTrue();
        assertThat(closed.isActive()).isFalse();
        assertThat(closed.getCheckOutDate()).isEqualTo(CHECK_IN.plusDays(2));
        assertThat(closed)
                .usingRecursiveComparison()
                .ignoringFields("checkOutDate")
                .isEqualTo(active);
    }

    @Test
    @DisplayName("Una estancia ya cerrada no admite otro Check-Out")
    void checkOut_twice_isRejected() {
        Stay closed = stay(CHECK_IN.plusDays(1));

        assertThatThrownBy(() -> closed.checkOut(CHECK_IN.plusDays(2))).isInstanceOf(IllegalStateException.class);
    }

    private static Stay stay(LocalDate checkOutDate) {
        return new Stay(
                UUID.randomUUID(),
                "RSV-8D02E5A4",
                UUID.randomUUID(),
                "BOOKING",
                CHECK_IN,
                checkOutDate,
                CHECK_IN,
                CHECK_IN.plusDays(2),
                "Ana",
                "Pérez",
                "123",
                4);
    }
}
