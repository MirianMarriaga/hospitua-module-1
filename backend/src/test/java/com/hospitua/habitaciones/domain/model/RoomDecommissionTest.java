package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.DomainError;
import com.hospitua.habitaciones.domain.exception.InvalidDecommissionReasonException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RoomDecommissionTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-09T15:00:00-05:00");

    @Test
    @DisplayName("La baja guarda motivo, detalle recortado, Administrador y la marca recibida, sin reactivación")
    void decommission_keepsReasonAndAuthor() {
        UUID roomId = UUID.randomUUID();

        RoomDecommission decommission = RoomDecommission.decommission(
                roomId, DecommissionReason.FLOOR_CLOSURE, "  Cierre del piso 3.  ", "lmendez", NOW);

        assertThat(decommission.getId()).isNotNull();
        assertThat(decommission.getRoomId()).isEqualTo(roomId);
        assertThat(decommission.getReasonCategory()).isEqualTo(DecommissionReason.FLOOR_CLOSURE);
        assertThat(decommission.getReasonDetail()).isEqualTo("Cierre del piso 3.");
        assertThat(decommission.getDecommissionedBy()).isEqualTo("lmendez");
        assertThat(decommission.getDecommissionDateTime()).isEqualTo(NOW);
        assertThat(decommission.getReactivatedBy()).isNull();
        assertThat(decommission.getReactivationDateTime()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = DecommissionReason.class, names = "OTHER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T010: fuera de OTHER el detalle es opcional; vacío o solo espacios queda nulo")
    void normalizeDetail_optionalOutsideOther(DecommissionReason reason) {
        assertThat(RoomDecommission.normalizeDetail(reason, null)).isNull();
        assertThat(RoomDecommission.normalizeDetail(reason, "   ")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("T010: OTHER sin detalle o con solo espacios se rechaza con VALIDATION_ERROR en reasonDetail")
    void normalizeDetail_otherRequiresDetail(String detail) {
        assertThatThrownBy(() -> RoomDecommission.normalizeDetail(DecommissionReason.OTHER, detail))
                .isInstanceOfSatisfying(InvalidDecommissionReasonException.class, ex -> {
                    assertThat(ex.error()).isEqualTo(DomainError.VALIDATION_ERROR);
                    assertThat(ex.getMessage()).isEqualTo("Escribe el motivo de la baja.");
                    assertThat(ex.details()).containsEntry("field", "reasonDetail");
                });
    }

    @Test
    @DisplayName("T010: el detalle admite 500 caracteres (sin contar los espacios de los extremos) y rechaza 501")
    void normalizeDetail_maxLength() {
        String max = "a".repeat(500);

        assertThat(RoomDecommission.normalizeDetail(DecommissionReason.OTHER, "  " + max + "  "))
                .isEqualTo(max);
        assertThatThrownBy(() -> RoomDecommission.normalizeDetail(DecommissionReason.FLOOR_CLOSURE, max + "a"))
                .isInstanceOf(InvalidDecommissionReasonException.class)
                .hasMessage("El detalle admite máximo 500 caracteres.");
    }

    @Test
    @DisplayName("Dos bajas son la misma si tienen el mismo id, aunque cambie la reactivación")
    void equality_byId() {
        UUID id = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        RoomDecommission open =
                new RoomDecommission(id, roomId, DecommissionReason.OTHER, "Cierre.", null, NOW, null, null);
        RoomDecommission reactivated = new RoomDecommission(
                id, roomId, DecommissionReason.OTHER, "Cierre.", null, NOW, "lmendez", NOW.plusDays(1));

        assertThat(reactivated).isEqualTo(open).hasSameHashCodeAs(open);
        assertThat(reactivated.getReactivatedBy()).isEqualTo("lmendez");
        assertThat(reactivated.getReactivationDateTime()).isEqualTo(NOW.plusDays(1));
        assertThat(RoomDecommission.decommission(roomId, DecommissionReason.FLOOR_CLOSURE, null, "lmendez", NOW))
                .isNotEqualTo(open)
                .isNotEqualTo(null);
    }

    @Test
    @DisplayName("Solo OTHER exige el detalle")
    void requiresDetail_onlyOther() {
        assertThat(DecommissionReason.OTHER.requiresDetail()).isTrue();
        assertThat(DecommissionReason.PERMANENT_REMODELING.requiresDetail()).isFalse();
        assertThat(DecommissionReason.FLOOR_CLOSURE.requiresDetail()).isFalse();
        assertThat(DecommissionReason.ADMINISTRATIVE_DECISION.requiresDetail()).isFalse();
    }
}
