package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableResult;
import com.hospitua.habitaciones.application.dto.ReactivateRoomCommand;
import com.hospitua.habitaciones.application.dto.ReactivationResultDto;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.UnknownDecommissionException;
import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReactivateRoomServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String ADMIN = "lmendez";
    private static final OffsetDateTime DECOMMISSIONED_AT = OffsetDateTime.parse("2026-10-09T15:00:00-05:00");
    private static final OffsetDateTime REACTIVATED_AT = OffsetDateTime.parse("2026-10-20T09:30:00-05:00");

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private RoomDecommissionRepositoryPort decommissionRepository;

    @Mock
    private MarkRoomAvailableUseCase markRoomAvailableUseCase;

    private ReactivateRoomService service;
    private RoomDecommission decommission;

    @BeforeEach
    void setUp() {
        service = new ReactivateRoomService(roomRepository, decommissionRepository, markRoomAvailableUseCase);
        decommission = RoomDecommission.decommission(
                ROOM_ID, DecommissionReason.FLOOR_CLOSURE, "Cierre del piso 3.", "admin", DECOMMISSIONED_AT);
    }

    @Test
    @DisplayName(
            "T011: Inactive → Available vía el puerto con MARK_AVAILABLE y la baja queda revertida por el Administrador")
    void reactivate_inactiveRoom() {
        givenLockedRoom(RoomStatus.Inactive);
        when(decommissionRepository.findById(decommission.getId())).thenReturn(Optional.of(decommission));
        when(markRoomAvailableUseCase.markAvailable(any()))
                .thenReturn(new MarkRoomAvailableResult(ROOM_ID, RoomStatus.Inactive, REACTIVATED_AT));

        ReactivationResultDto result = service.reactivate(command(decommission.getId()));

        assertThat(result)
                .isEqualTo(
                        new ReactivationResultDto(ROOM_ID, "305", 3, RoomCategory.SUITE, RoomStatus.Available, false));
        ArgumentCaptor<RoomDecommission> saved = ArgumentCaptor.forClass(RoomDecommission.class);
        InOrder order = inOrder(roomRepository, decommissionRepository, markRoomAvailableUseCase);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(decommissionRepository).findById(decommission.getId());
        order.verify(markRoomAvailableUseCase)
                .markAvailable(new MarkRoomAvailableCommand(ROOM_ID, SourceFlow.MARK_AVAILABLE, ADMIN, null));
        order.verify(decommissionRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(decommission.getId());
        assertThat(saved.getValue().getReactivatedBy()).isEqualTo(ADMIN);
        assertThat(saved.getValue().getReactivationDateTime()).isEqualTo(REACTIVATED_AT);
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"Available", "Occupied", "PendingCleaning", "DisabledForRepairs", "TechnicalBlock"})
    @DisplayName("T012: fuera de Inactive, y sin ser un reintento, responde ROOM_INVALID_STATE sin cambios")
    void reactivate_notInactive_rejected(RoomStatus status) {
        givenLockedRoom(status);
        when(decommissionRepository.findById(decommission.getId())).thenReturn(Optional.of(decommission));

        assertThatThrownBy(() -> service.reactivate(command(decommission.getId())))
                .isInstanceOfSatisfying(InvalidRoomTransitionException.class, ex -> {
                    assertThat(ex.getCurrentStatus()).isEqualTo(status);
                    assertThat(ex.getMessage())
                            .isEqualTo("La habitación no está Inactiva (estado actual: " + status.displayName()
                                    + "); no es posible marcarla como disponible.");
                });
        verifyNoInteractions(markRoomAvailableUseCase);
        verify(decommissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("T013: un reintento con una baja ya revertida responde alreadyReactivated sin transición ni escritura")
    void reactivate_retryWithRevertedDecommission() {
        givenLockedRoom(RoomStatus.Available);
        RoomDecommission reverted = decommission.reactivate("admin", REACTIVATED_AT);
        when(decommissionRepository.findById(reverted.getId())).thenReturn(Optional.of(reverted));

        ReactivationResultDto result = service.reactivate(command(reverted.getId()));

        assertThat(result.alreadyReactivated()).isTrue();
        assertThat(result.status()).isEqualTo(RoomStatus.Available);
        verifyNoInteractions(markRoomAvailableUseCase);
        verify(decommissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una baja inexistente o de otra habitación responde VALIDATION_ERROR sin cambios")
    void reactivate_unknownDecommission() {
        givenLockedRoom(RoomStatus.Inactive);
        RoomDecommission otherRoom = RoomDecommission.decommission(
                UUID.randomUUID(), DecommissionReason.OTHER, "Otra.", "admin", DECOMMISSIONED_AT);
        when(decommissionRepository.findById(otherRoom.getId())).thenReturn(Optional.of(otherRoom));
        UUID missing = UUID.randomUUID();
        when(decommissionRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(command(otherRoom.getId())))
                .isInstanceOf(UnknownDecommissionException.class);
        assertThatThrownBy(() -> service.reactivate(command(missing)))
                .isInstanceOf(UnknownDecommissionException.class)
                .hasMessage("No se pudo identificar la baja de la habitación. Vuelve a abrir la confirmación.");
        verifyNoInteractions(markRoomAvailableUseCase);
    }

    @Test
    @DisplayName("Una habitación inexistente responde ROOM_NOT_FOUND")
    void reactivate_unknownRoom() {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reactivate(command(decommission.getId())))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(decommissionRepository, markRoomAvailableUseCase);
    }

    private void givenLockedRoom(RoomStatus status) {
        when(roomRepository.lockById(ROOM_ID))
                .thenReturn(Optional.of(
                        new Room(ROOM_ID, "305", 3, RoomCategory.SUITE, 4, new BigDecimal("320000"), status, null, 0)));
    }

    private static ReactivateRoomCommand command(UUID decommissionId) {
        return new ReactivateRoomCommand(ROOM_ID, decommissionId, ADMIN);
    }
}
