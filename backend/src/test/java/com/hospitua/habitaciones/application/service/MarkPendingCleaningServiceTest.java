package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkPendingCleaningServiceTest {

    @Mock
    private RoomRepositoryPort roomRepositoryPort;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @InjectMocks
    private MarkPendingCleaningService service;

    private Room createRoom(UUID id, RoomStatus status) {
        return new Room(id, "201", 2, RoomCategory.SENCILLA, 1, new BigDecimal("100.00"), status, null, 0);
    }

    @ParameterizedTest
    @CsvSource({
        "Occupied, CHECK_OUT",
        "DisabledForRepairs, CONFIRM_REPAIR_END",
        "TechnicalBlock, CONFIRM_REPAIR_END",
        "InCleaning, RELEASE_CLEANING_TASK"
    })
    @DisplayName("T004: Transiciones permitidas hacia PendingCleaning")
    void shouldAllowValidTransitionsToPendingCleaning(RoomStatus originStatus, SourceFlow sourceFlow) {
        UUID roomId = UUID.randomUUID();
        String actorId = "user123";
        Room room = createRoom(roomId, originStatus);
        OffsetDateTime expectedTimestamp = OffsetDateTime.now();

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        eq(roomId), eq(RoomStatus.PendingCleaning), eq(actorId), eq(sourceFlow), isNull()))
                .thenReturn(expectedTimestamp);

        MarkPendingCleaningCommand command = new MarkPendingCleaningCommand(roomId, sourceFlow, actorId);
        MarkPendingCleaningResult result = service.markPendingCleaning(command);

        assertThat(result.roomId()).isEqualTo(roomId);
        assertThat(result.previousStatus()).isEqualTo(originStatus);
        assertThat(result.transitionDateTime()).isEqualTo(expectedTimestamp);

        verify(transitionRoomStateUseCase).transition(roomId, RoomStatus.PendingCleaning, actorId, sourceFlow, null);
    }

    @ParameterizedTest
    @CsvSource({"InCleaning, CHECK_OUT", "InCleaning, CONFIRM_REPAIR_END", "InCleaning, START_CLEANING"})
    @DisplayName("T004: InCleaning con sourceFlow distinto a RELEASE_CLEANING_TASK es rechazado")
    void shouldRejectInCleaningWhenSourceFlowIsNotReleaseCleaningTask(RoomStatus originStatus, SourceFlow sourceFlow) {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId, originStatus);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));

        MarkPendingCleaningCommand command = new MarkPendingCleaningCommand(roomId, sourceFlow, "user1");

        assertThatThrownBy(() -> service.markPendingCleaning(command))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .satisfies(e -> assertThat(((InvalidRoomTransitionException) e).getCurrentStatus())
                        .isEqualTo(RoomStatus.InCleaning));

        verify(transitionRoomStateUseCase, never()).transition(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"Available, CHECK_OUT", "Reserved, CHECK_OUT", "PendingCleaning, CHECK_OUT", "Inactive, CHECK_OUT"})
    @DisplayName("T004: Estados no permitidos lanzan InvalidRoomTransitionException")
    void shouldRejectInvalidOriginStatuses(RoomStatus invalidOrigin, SourceFlow sourceFlow) {
        UUID roomId = UUID.randomUUID();
        Room room = createRoom(roomId, invalidOrigin);

        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.of(room));
        when(transitionRoomStateUseCase.transition(
                        eq(roomId), eq(RoomStatus.PendingCleaning), any(), eq(sourceFlow), isNull()))
                .thenThrow(new InvalidRoomTransitionException(invalidOrigin));

        MarkPendingCleaningCommand command = new MarkPendingCleaningCommand(roomId, sourceFlow, "user1");

        assertThatThrownBy(() -> service.markPendingCleaning(command))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .satisfies(e -> assertThat(((InvalidRoomTransitionException) e).getCurrentStatus())
                        .isEqualTo(invalidOrigin));
    }

    @Test
    @DisplayName("T004: Habitación no encontrada lanza RoomNotFoundException")
    void shouldThrowWhenRoomNotFound() {
        UUID roomId = UUID.randomUUID();
        when(roomRepositoryPort.findById(roomId)).thenReturn(Optional.empty());

        MarkPendingCleaningCommand command = new MarkPendingCleaningCommand(roomId, SourceFlow.CHECK_OUT, "user1");

        assertThatThrownBy(() -> service.markPendingCleaning(command)).isInstanceOf(RoomNotFoundException.class);
    }
}
