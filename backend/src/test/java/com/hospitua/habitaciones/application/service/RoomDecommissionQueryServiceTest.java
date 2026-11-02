package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.RoomDecommissionInfoDto;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomDecommissionQueryServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final OffsetDateTime DECOMMISSIONED_AT = OffsetDateTime.parse("2026-10-09T15:00:00-05:00");

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private RoomDecommissionRepositoryPort decommissionRepository;

    private RoomDecommissionQueryService service;

    @BeforeEach
    void setUp() {
        service = new RoomDecommissionQueryService(roomRepository, decommissionRepository);
    }

    @Test
    @DisplayName("T014: devuelve la habitación con la fecha, la categoría y el detalle de su baja vigente")
    void getCurrent_returnsRoomAndDecommission() {
        givenRoom(RoomStatus.Inactive);
        RoomDecommission decommission = givenCurrent("Cierre del piso 3.");

        RoomDecommissionInfoDto info = service.getCurrent(ROOM_ID);

        assertThat(info)
                .isEqualTo(new RoomDecommissionInfoDto(
                        ROOM_ID,
                        "305",
                        3,
                        RoomCategory.SUITE,
                        RoomStatus.Inactive,
                        decommission.getId(),
                        DECOMMISSIONED_AT,
                        DecommissionReason.FLOOR_CLOSURE,
                        "Cierre del piso 3."));
    }

    @Test
    @DisplayName("T014: el detalle es nulo si la baja no lo tiene")
    void getCurrent_withoutDetail() {
        givenRoom(RoomStatus.Inactive);
        givenCurrent(null);

        assertThat(service.getCurrent(ROOM_ID).reasonDetail()).isNull();
    }

    @Test
    @DisplayName("Fuera de Inactive responde ROOM_INVALID_STATE sin buscar la baja")
    void getCurrent_notInactive() {
        givenRoom(RoomStatus.Available);

        assertThatThrownBy(() -> service.getCurrent(ROOM_ID))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .hasMessage(
                        "La habitación no está Inactiva (estado actual: Disponible); no es posible marcarla como disponible.");
        verifyNoInteractions(decommissionRepository);
    }

    @Test
    @DisplayName("Una habitación inexistente responde ROOM_NOT_FOUND")
    void getCurrent_unknownRoom() {
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(ROOM_ID)).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    @DisplayName("Una habitación Inactive sin baja es un dato inconsistente: error inesperado")
    void getCurrent_inactiveWithoutDecommission() {
        givenRoom(RoomStatus.Inactive);
        when(decommissionRepository.findCurrentByRoomId(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(ROOM_ID)).isInstanceOf(IllegalStateException.class);
    }

    private void givenRoom(RoomStatus status) {
        when(roomRepository.findById(ROOM_ID))
                .thenReturn(Optional.of(
                        new Room(ROOM_ID, "305", 3, RoomCategory.SUITE, 4, new BigDecimal("320000"), status, null, 0)));
    }

    private RoomDecommission givenCurrent(String detail) {
        RoomDecommission decommission = RoomDecommission.decommission(
                ROOM_ID, DecommissionReason.FLOOR_CLOSURE, detail, "admin", DECOMMISSIONED_AT);
        when(decommissionRepository.findCurrentByRoomId(ROOM_ID)).thenReturn(Optional.of(decommission));
        return decommission;
    }
}
