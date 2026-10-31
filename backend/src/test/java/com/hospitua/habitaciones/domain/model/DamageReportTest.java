package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.DomainError;
import com.hospitua.habitaciones.domain.exception.DomainException;
import com.hospitua.habitaciones.domain.exception.InvalidDamageDescriptionException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DamageReportTest {

    private static final OffsetDateTime REPORTED_AT = OffsetDateTime.parse("2026-10-08T11:20:00-05:00");

    @Test
    @DisplayName("report crea un reporte con id nuevo y la descripción sin espacios en los extremos")
    void report_stripsDescription() {
        UUID roomId = UUID.randomUUID();

        DamageReport report = DamageReport.report(roomId, "mtorres", "  Ventana fisurada.  ", REPORTED_AT);

        assertThat(report.getId()).isNotNull();
        assertThat(report.getRoomId()).isEqualTo(roomId);
        assertThat(report.getAuthorId()).isEqualTo("mtorres");
        assertThat(report.getDamageDescription()).isEqualTo("Ventana fisurada.");
        assertThat(report.getReportDateTime()).isEqualTo(REPORTED_AT);
    }

    @Test
    @DisplayName("Una descripción de 500 caracteres es válida")
    void normalizeDescription_maxLength() {
        String description = "a".repeat(DamageReport.MAX_DESCRIPTION_LENGTH);

        assertThat(DamageReport.normalizeDescription(description)).isEqualTo(description);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("Una descripción nula, vacía o solo con espacios es VALIDATION_ERROR sobre damageDescription")
    void normalizeDescription_blank(String description) {
        assertInvalid(description);
    }

    @Test
    @DisplayName("Una descripción de más de 500 caracteres es VALIDATION_ERROR")
    void normalizeDescription_tooLong() {
        assertInvalid("a".repeat(DamageReport.MAX_DESCRIPTION_LENGTH + 1));
    }

    private static void assertInvalid(String description) {
        assertThatThrownBy(() -> DamageReport.normalizeDescription(description))
                .isInstanceOf(InvalidDamageDescriptionException.class)
                .hasMessage("La descripción del daño es obligatoria (máximo 500 caracteres).")
                .satisfies(ex -> {
                    DomainException domain = (DomainException) ex;
                    assertThat(domain.error()).isEqualTo(DomainError.VALIDATION_ERROR);
                    assertThat(domain.details()).containsEntry("field", "damageDescription");
                });
    }
}
