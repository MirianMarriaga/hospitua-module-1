package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.InvalidGuestDataException;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CheckInGuestTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate BIRTH = LocalDate.of(1990, 5, 20);

    @Test
    @DisplayName("T011: un colombiano no lleva lugares; se normaliza la nacionalidad y el tipo de documento")
    void colombian_dropsPlaces() {
        CheckInGuest guest =
                CheckInGuest.of(0, " Ana ", "Pérez", "cc", " 123 ", "colombia", BIRTH, "Lima", "Quito", true, TODAY);

        assertThat(guest.firstName()).isEqualTo("Ana");
        assertThat(guest.documentType()).isEqualTo("CC");
        assertThat(guest.documentNumber()).isEqualTo("123");
        assertThat(guest.nationality()).isEqualTo("Colombia");
        assertThat(guest.originPlace()).isNull();
        assertThat(guest.destinationPlace()).isNull();
        assertThat(guest.reservationGuest()).isTrue();
    }

    @Test
    @DisplayName("Un extranjero conserva el lugar de procedencia y el de destino")
    void foreigner_keepsPlaces() {
        CheckInGuest guest = CheckInGuest.of(
                1, "John", "Smith", "PAS", "X99", "Estados Unidos", BIRTH, "Miami", "Cartagena", false, TODAY);

        assertThat(guest.nationality()).isEqualTo("Estados Unidos");
        assertThat(guest.originPlace()).isEqualTo("Miami");
        assertThat(guest.destinationPlace()).isEqualTo("Cartagena");
    }

    @Test
    @DisplayName("T010: a un extranjero sin lugar de procedencia o de destino se le pide el campo, con su posición")
    void foreigner_requiresPlaces() {
        assertThatThrownBy(() -> CheckInGuest.of(
                        2, "John", "Smith", "PAS", "X99", "Perú", BIRTH, " ", "Cartagena", false, TODAY))
                .isInstanceOf(InvalidGuestDataException.class)
                .extracting(e -> ((InvalidGuestDataException) e).details())
                .isEqualTo(Map.of("field", "originPlace", "guestIndex", 2));
        assertThatThrownBy(() ->
                        CheckInGuest.of(2, "John", "Smith", "PAS", "X99", "Perú", BIRTH, "Lima", null, false, TODAY))
                .extracting(e -> ((InvalidGuestDataException) e).details().get("field"))
                .isEqualTo("destinationPlace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DNI", "XX", "pasaporte"})
    @DisplayName("T010: un tipo de documento fuera del catálogo es VALIDATION_ERROR en documentType")
    void unknownDocumentType_isRejected(String type) {
        assertThatThrownBy(() ->
                        CheckInGuest.of(0, "Ana", "Pérez", type, "123", "Colombia", BIRTH, null, null, false, TODAY))
                .isInstanceOf(InvalidGuestDataException.class)
                .hasMessageContaining("RC, TI, CC, CE, PAS o NIT")
                .extracting(e -> ((InvalidGuestDataException) e).details().get("field"))
                .isEqualTo("documentType");
    }

    @Test
    @DisplayName("T010/T011: una fecha de nacimiento ausente, de hoy o futura es VALIDATION_ERROR en birthDate")
    void birthDate_mustBeBeforeToday() {
        for (LocalDate birthDate : new LocalDate[] {null, TODAY, TODAY.plusDays(1)}) {
            assertThatThrownBy(() -> CheckInGuest.of(
                            0, "Ana", "Pérez", "CC", "123", "Colombia", birthDate, null, null, false, TODAY))
                    .extracting(e -> ((InvalidGuestDataException) e).details().get("field"))
                    .isEqualTo("birthDate");
        }
    }

    @Test
    @DisplayName("Un campo obligatorio vacío o más largo que su columna es VALIDATION_ERROR en ese campo")
    void requiredAndLength() {
        assertThatThrownBy(() ->
                        CheckInGuest.of(0, null, "Pérez", "CC", "123", "Colombia", BIRTH, null, null, false, TODAY))
                .extracting(e -> ((InvalidGuestDataException) e).details().get("field"))
                .isEqualTo("firstName");
        assertThatThrownBy(() -> CheckInGuest.of(
                        0, "Ana", "Pérez", "CC", "9".repeat(51), "Colombia", BIRTH, null, null, false, TODAY))
                .hasMessage("El campo admite máximo 50 caracteres.")
                .extracting(e -> ((InvalidGuestDataException) e).details().get("field"))
                .isEqualTo("documentNumber");
        assertThatThrownBy(() -> CheckInGuest.of(0, "Ana", "Pérez", "CC", "123", "", BIRTH, null, null, false, TODAY))
                .extracting(e -> ((InvalidGuestDataException) e).details().get("field"))
                .isEqualTo("nationality");
    }
}
