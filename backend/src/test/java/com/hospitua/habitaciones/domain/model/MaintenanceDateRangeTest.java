package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.InvalidDateRangeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MaintenanceDateRangeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final MaintenanceLimits LIMITS = new MaintenanceLimits(90, 365);

    @Nested
    @DisplayName("forAvailability (consulta de mantenimientos)")
    class ForAvailability {

        @Test
        @DisplayName("T007: fin anterior al inicio → END_BEFORE_START con su mensaje")
        void endBeforeStart() {
            assertRule(
                    () -> MaintenanceDateRange.forAvailability(TODAY, TODAY.plusDays(5), TODAY.plusDays(4)),
                    "END_BEFORE_START",
                    "La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        @Test
        @DisplayName("T007: un rango que termina antes de hoy → RANGE_IN_PAST")
        void rangeInPast() {
            assertRule(
                    () -> MaintenanceDateRange.forAvailability(TODAY, TODAY.minusDays(5), TODAY.minusDays(1)),
                    "RANGE_IN_PAST",
                    "El rango consultado termina antes de hoy.");
        }

        @Test
        @DisplayName("T007: un rango que empieza antes de hoy se evalúa desde hoy")
        void startBeforeToday_clippedToToday() {
            assertThat(MaintenanceDateRange.forAvailability(TODAY, TODAY.minusDays(3), TODAY.plusDays(2)))
                    .isEqualTo(new MaintenanceDateRange(TODAY, TODAY.plusDays(2)));
        }

        @Test
        @DisplayName("T007: inicio igual al fin es válido, también hoy")
        void singleDay_isValid() {
            assertThat(MaintenanceDateRange.forAvailability(TODAY, TODAY, TODAY))
                    .isEqualTo(new MaintenanceDateRange(TODAY, TODAY));
        }

        @Test
        @DisplayName("T007: sin límites: más de 90 días de duración o más de 365 de antelación son válidos (FR-007)")
        void noLimits() {
            assertThat(MaintenanceDateRange.forAvailability(TODAY, TODAY, TODAY.plusDays(200))
                            .lastDay())
                    .isEqualTo(TODAY.plusDays(200));
            assertThat(MaintenanceDateRange.forAvailability(TODAY, TODAY.plusDays(500), TODAY.plusDays(501))
                            .firstDay())
                    .isEqualTo(TODAY.plusDays(500));
        }
    }

    @Nested
    @DisplayName("forScheduling (programar bloqueo técnico)")
    class ForScheduling {

        @Test
        @DisplayName("T002: fin anterior al inicio → END_BEFORE_START")
        void endBeforeStart() {
            assertRule(
                    () -> MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY.plusDays(3), TODAY.plusDays(2)),
                    "END_BEFORE_START",
                    "La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        @Test
        @DisplayName("T002: inicio anterior a hoy → START_IN_PAST, aunque el fin sea futuro (no recorta)")
        void startInPast() {
            assertRule(
                    () -> MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY.minusDays(1), TODAY.plusDays(2)),
                    "START_IN_PAST",
                    "La fecha de inicio no puede ser anterior a hoy.");
        }

        @Test
        @DisplayName("T002: duración (fin − inicio) mayor al límite → RANGE_TOO_LONG con el límite en el mensaje")
        void rangeTooLong() {
            assertThat(MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY, TODAY.plusDays(90))
                            .lastDay())
                    .isEqualTo(TODAY.plusDays(90));
            assertRule(
                    () -> MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY, TODAY.plusDays(91)),
                    "RANGE_TOO_LONG",
                    "El mantenimiento no puede durar más de 90 días.");
        }

        @Test
        @DisplayName("T002: antelación (inicio − hoy) mayor al límite → START_TOO_FAR con el límite en el mensaje")
        void startTooFar() {
            assertThat(MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY.plusDays(365), TODAY.plusDays(366))
                            .firstDay())
                    .isEqualTo(TODAY.plusDays(365));
            assertRule(
                    () -> MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY.plusDays(366), TODAY.plusDays(366)),
                    "START_TOO_FAR",
                    "Solo se puede programar con hasta 365 días de anticipación.");
        }

        @Test
        @DisplayName("T002: los límites salen de MaintenanceLimits, no son fijos")
        void configurableLimits() {
            MaintenanceLimits strict = new MaintenanceLimits(2, 10);
            assertRule(
                    () -> MaintenanceDateRange.forScheduling(TODAY, strict, TODAY, TODAY.plusDays(3)),
                    "RANGE_TOO_LONG",
                    "El mantenimiento no puede durar más de 2 días.");
            assertRule(
                    () -> MaintenanceDateRange.forScheduling(TODAY, strict, TODAY.plusDays(11), TODAY.plusDays(11)),
                    "START_TOO_FAR",
                    "Solo se puede programar con hasta 10 días de anticipación.");
        }

        @Test
        @DisplayName("T002: un solo día que empieza hoy es válido")
        void singleDayToday_isValid() {
            assertThat(MaintenanceDateRange.forScheduling(TODAY, LIMITS, TODAY, TODAY))
                    .isEqualTo(new MaintenanceDateRange(TODAY, TODAY));
        }
    }

    @Test
    @DisplayName("includes: los dos extremos cuentan")
    void includes_inclusive() {
        MaintenanceDateRange range = new MaintenanceDateRange(TODAY, TODAY.plusDays(2));

        assertThat(range.includes(TODAY.minusDays(1))).isFalse();
        assertThat(range.includes(TODAY)).isTrue();
        assertThat(range.includes(TODAY.plusDays(2))).isTrue();
        assertThat(range.includes(TODAY.plusDays(3))).isFalse();
    }

    @Test
    @DisplayName("El constructor no acepta un último día anterior al primero (las fábricas ya lo validaron)")
    void constructor_rejectsInvertedRange() {
        assertThatThrownBy(() -> new MaintenanceDateRange(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MaintenanceConflict: rango del informe, cruce de un solo día y fin no anterior al inicio")
    void maintenanceConflict() {
        TechnicalBlockReport report = TechnicalBlockReport.schedule(
                UUID.randomUUID(),
                "mtorres",
                "Cambio de tubería.",
                TODAY.plusDays(1),
                TODAY.plusDays(4),
                OffsetDateTime.parse("2026-10-08T15:00:00-05:00"));

        assertThat(MaintenanceConflict.of(report))
                .isEqualTo(new MaintenanceConflict(TODAY.plusDays(1), TODAY.plusDays(4)));
        assertThat(MaintenanceConflict.onlyOn(TODAY)).isEqualTo(new MaintenanceConflict(TODAY, TODAY));
        assertThatThrownBy(() -> new MaintenanceConflict(TODAY, TODAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertRule(Runnable call, String rule, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(InvalidDateRangeException.class, ex -> {
            assertThat(ex.rule()).isEqualTo(rule);
            assertThat(ex.details()).containsEntry("rule", rule);
            assertThat(ex.getMessage()).isEqualTo(message);
        });
    }
}
