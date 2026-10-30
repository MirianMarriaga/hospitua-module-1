package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class ArrivalNoticeTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "DisabledForRepairs, No disponible: Inhabilitada por reparaciones",
        "TechnicalBlock, No disponible: Bloqueo técnico",
        "Inactive, No disponible: Inactiva"
    })
    @DisplayName("FR-005: en mantenimiento o fuera de servicio la fila lleva la alerta \"No disponible: [Estado]\"")
    void outOfService_isUnavailableAlert(RoomStatus status, String text) {
        assertThat(ArrivalNotice.forStatus(status)).hasValue(new ArrivalNotice(ArrivalNoticeType.UNAVAILABLE, text));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "Occupied, Ocupada: se apartará al terminar la limpieza",
        "PendingCleaning, Pendiente de limpieza: se apartará al terminar la limpieza",
        "InCleaning, En limpieza: se apartará al terminar la limpieza"
    })
    @DisplayName("FR-005: ocupada o en limpieza la fila lleva el indicador informativo")
    void occupiedOrCleaning_isPendingAssignment(RoomStatus status, String text) {
        assertThat(ArrivalNotice.forStatus(status))
                .hasValue(new ArrivalNotice(ArrivalNoticeType.PENDING_ASSIGNMENT, text));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
            value = RoomStatus.class,
            names = {"Reserved", "Available"})
    @DisplayName("FR-005: una habitación Reserved o Available no lleva aviso")
    void reservedOrAvailable_hasNoNotice(RoomStatus status) {
        assertThat(ArrivalNotice.forStatus(status)).isEqualTo(Optional.empty());
    }
}
