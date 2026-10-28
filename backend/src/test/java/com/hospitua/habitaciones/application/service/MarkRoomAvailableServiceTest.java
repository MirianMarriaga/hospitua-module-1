package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableResult;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MarkRoomAvailableServiceTest {

    private RoomRepositoryPort roomRepositoryPort;
    private TransitionRoomStateUseCase transitionRoomStateUseCase;
    private MarkRoomAvailableService service;

    private final UUID roomId = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-27T10:00:00-05:00");

    @BeforeEach
    void setUp() {
        roomRepositoryPort = Mockito.mock(RoomRepositoryPort.class);
        transitionRoomStateUseCase = Mockito.mock(TransitionRoomStateUseCase.class);
        service = new MarkRoomAvailableService(roomRepositoryPort, transitionRoomStateUseCase);
    }

    private Room createRoom(RoomStatus status, String reservationRef) {
        return new Room(roomId, "201", 2, RoomCategory.DOBLE, 2, BigDecimal.valueOf(180000), status, reservationRef, 0);
    }

    @Test
    @DisplayName("InCleaning -> Available con CONFIRM_CLEANING_END y actorId")
    void markAvailable_fromInCleaning_success() {
        Room room = createRoom(RoomStatus.InCleaning, null);
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        roomId, RoomStatus.Available, "cleantasker", SourceFlow.CONFIRM_CLEANING_END, null))
                .thenReturn(now);

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, "cleantasker", null);

        MarkRoomAvailableResult result = service.markAvailable(command);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.previousStatus()).isEqualTo(RoomStatus.InCleaning);
        assertThat(result.transitionDateTime()).isEqualTo(now);

        verify(transitionRoomStateUseCase)
                .transition(roomId, RoomStatus.Available, "cleantasker", SourceFlow.CONFIRM_CLEANING_END, null);
    }

    @Test
    @DisplayName("Reserved -> Available con MARK_AVAILABLE, actorId nulo y reservationRef vinculada")
    void markAvailable_fromReserved_success() {
        Room room = createRoom(RoomStatus.Reserved, "RES-101");
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        roomId, RoomStatus.Available, null, SourceFlow.MARK_AVAILABLE, "RES-101"))
                .thenReturn(now);

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, null, "RES-101");

        MarkRoomAvailableResult result = service.markAvailable(command);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.previousStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(result.transitionDateTime()).isEqualTo(now);

        verify(transitionRoomStateUseCase)
                .transition(roomId, RoomStatus.Available, null, SourceFlow.MARK_AVAILABLE, "RES-101");
    }

    @Test
    @DisplayName("Reserved -> Available con reservationRef diferente falla con InvalidRoomTransitionException")
    void markAvailable_fromReserved_differentReservationRef_throws() {
        Room room = createRoom(RoomStatus.Reserved, "RES-101");
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, null, "RES-999");

        assertThatThrownBy(() -> service.markAvailable(command))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .satisfies(ex -> assertThat(((InvalidRoomTransitionException) ex).getCurrentStatus())
                        .isEqualTo(RoomStatus.Reserved));
    }

    @Test
    @DisplayName("Inactive -> Available con MARK_AVAILABLE y actorId de Administrador")
    void markAvailable_fromInactive_success() {
        Room room = createRoom(RoomStatus.Inactive, null);
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        roomId, RoomStatus.Available, "adminUser", SourceFlow.MARK_AVAILABLE, null))
                .thenReturn(now);

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, "adminUser", null);

        MarkRoomAvailableResult result = service.markAvailable(command);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.previousStatus()).isEqualTo(RoomStatus.Inactive);
        assertThat(result.transitionDateTime()).isEqualTo(now);
    }

    @Test
    @DisplayName("Inactive -> Available sin actorId falla con IllegalArgumentException")
    void markAvailable_fromInactive_missingActorId_throws() {
        Room room = createRoom(RoomStatus.Inactive, null);
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomAvailableCommand command = new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, null, null);

        assertThatThrownBy(() -> service.markAvailable(command)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CONFIRM_CLEANING_END sobre estado distinto de InCleaning falla con InvalidRoomTransitionException")
    void markAvailable_confirmCleaningEnd_fromOccupied_throws() {
        Room room = createRoom(RoomStatus.Occupied, null);
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, "cleaner", null);

        assertThatThrownBy(() -> service.markAvailable(command))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .satisfies(ex -> assertThat(((InvalidRoomTransitionException) ex).getCurrentStatus())
                        .isEqualTo(RoomStatus.Occupied));
    }

    @Test
    @DisplayName(
            "MARK_AVAILABLE sobre estado no permitido (ej. Available u Occupied) falla con InvalidRoomTransitionException")
    void markAvailable_markAvailable_fromOccupied_throws() {
        Room room = createRoom(RoomStatus.Occupied, null);
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, "adminUser", null);

        assertThatThrownBy(() -> service.markAvailable(command))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .satisfies(ex -> assertThat(((InvalidRoomTransitionException) ex).getCurrentStatus())
                        .isEqualTo(RoomStatus.Occupied));
    }

    @Test
    @DisplayName("Habitación no encontrada lanza RoomNotFoundException")
    void markAvailable_roomNotFound_throws() {
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.empty());

        MarkRoomAvailableCommand command =
                new MarkRoomAvailableCommand(roomId, SourceFlow.CONFIRM_CLEANING_END, "cleaner", null);

        assertThatThrownBy(() -> service.markAvailable(command)).isInstanceOf(RoomNotFoundException.class);
    }
}
