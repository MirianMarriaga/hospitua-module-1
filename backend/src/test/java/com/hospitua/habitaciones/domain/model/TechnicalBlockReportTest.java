package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TechnicalBlockReportTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 20);
    private static final LocalDate END = LocalDate.of(2026, 10, 22);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T15:00:00-05:00");

    @Test
    @DisplayName("schedule crea el informe en Scheduled con los datos recibidos")
    void schedule_startsScheduled() {
        UUID roomId = UUID.randomUUID();

        TechnicalBlockReport report =
                TechnicalBlockReport.schedule(roomId, "mtorres", "Cambio de tubería.", START, END, NOW);

        assertThat(report.getId()).isNotNull();
        assertThat(report.getRoomId()).isEqualTo(roomId);
        assertThat(report.getMaintenanceStaffMemberId()).isEqualTo("mtorres");
        assertThat(report.getReason()).isEqualTo("Cambio de tubería.");
        assertThat(report.getStartDate()).isEqualTo(START);
        assertThat(report.getEstimatedEndDate()).isEqualTo(END);
        assertThat(report.getReportDateTime()).isEqualTo(NOW);
        assertThat(report.getStatus()).isEqualTo(TechnicalBlockStatus.Scheduled);
    }

    @Test
    @DisplayName("El fin estimado no puede ser anterior al inicio (mismo CHECK que V3); un solo día es válido")
    void endBeforeStart_rejected() {
        assertThatThrownBy(() -> TechnicalBlockReport.schedule(UUID.randomUUID(), "m", "r", END, START, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(TechnicalBlockReport.schedule(UUID.randomUUID(), "m", "r", START, START, NOW)
                        .covers(START))
                .isTrue();
    }

    @ParameterizedTest
    @CsvSource({"2026-10-19,false", "2026-10-20,true", "2026-10-21,true", "2026-10-22,true", "2026-10-23,false"})
    @DisplayName("covers: el rango [inicio, fin estimado] es inclusivo")
    void covers_inclusiveRange(LocalDate day, boolean expected) {
        assertThat(scheduled().covers(day)).isEqualTo(expected);
    }

    @Test
    @DisplayName("isOverdue: solo un Applied cuyo fin estimado ya pasó")
    void isOverdue_onlyAppliedAfterEnd() {
        TechnicalBlockReport report = scheduled();
        assertThat(report.isOverdue(END.plusDays(1))).isFalse();

        report.apply();
        assertThat(report.isOverdue(END)).isFalse();
        assertThat(report.isOverdue(END.plusDays(1))).isTrue();
    }

    @Test
    @DisplayName("Transiciones válidas: Scheduled → Applied → Completed y Scheduled → Expired")
    void validTransitions() {
        TechnicalBlockReport applied = scheduled();
        applied.apply();
        assertThat(applied.getStatus()).isEqualTo(TechnicalBlockStatus.Applied);
        applied.complete();
        assertThat(applied.getStatus()).isEqualTo(TechnicalBlockStatus.Completed);

        TechnicalBlockReport expired = scheduled();
        expired.expire();
        assertThat(expired.getStatus()).isEqualTo(TechnicalBlockStatus.Expired);
    }

    @Test
    @DisplayName("Completed y Expired son definitivos; Scheduled no se completa ni Applied caduca")
    void invalidTransitions_rejected() {
        TechnicalBlockReport report = scheduled();
        assertThatThrownBy(report::complete).isInstanceOf(IllegalStateException.class);

        report.apply();
        assertThatThrownBy(report::expire).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(report::apply).isInstanceOf(IllegalStateException.class);

        report.complete();
        assertThatThrownBy(report::apply).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(report::complete).isInstanceOf(IllegalStateException.class);

        TechnicalBlockReport expired = scheduled();
        expired.expire();
        assertThatThrownBy(expired::apply).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(expired::expire).isInstanceOf(IllegalStateException.class);
        assertThat(expired.getStatus()).isEqualTo(TechnicalBlockStatus.Expired);
    }

    @Test
    @DisplayName("Dos informes son el mismo si tienen el mismo id, aunque cambie el estado")
    void equality_byId() {
        TechnicalBlockReport report = scheduled();
        TechnicalBlockReport sameId = new TechnicalBlockReport(
                report.getId(),
                report.getRoomId(),
                "otro",
                "Otro motivo.",
                START,
                END,
                NOW,
                TechnicalBlockStatus.Applied);

        assertThat(sameId).isEqualTo(report).hasSameHashCodeAs(report);
        assertThat(scheduled()).isNotEqualTo(report).isNotEqualTo(null);
        assertThat(report).isEqualTo(report);
    }

    @Test
    @DisplayName("ACTIVE agrupa los estados que cuentan como cruce, Scheduled y Applied, y no se puede modificar")
    void activeStatuses() {
        assertThat(TechnicalBlockStatus.ACTIVE)
                .containsExactlyInAnyOrder(TechnicalBlockStatus.Scheduled, TechnicalBlockStatus.Applied);
        assertThatThrownBy(() -> TechnicalBlockStatus.ACTIVE.add(TechnicalBlockStatus.Expired))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static TechnicalBlockReport scheduled() {
        return TechnicalBlockReport.schedule(UUID.randomUUID(), "mtorres", "Cambio de tubería.", START, END, NOW);
    }
}
