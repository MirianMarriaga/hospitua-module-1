package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.TaskNotOwnedException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseCleaningTaskServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "jramirez";
    private static final String ADMIN = "admin";
    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-10-08T09:15:00-05:00");
    private static final OffsetDateTime PENDING_AT = OffsetDateTime.parse("2026-10-08T09:40:00-05:00");

    @Mock
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private MarkPendingCleaningUseCase markPendingCleaningUseCase;

    private CleaningTask task;
    private ReleaseCleaningTaskService service;

    @BeforeEach
    void setUp() {
        task = CleaningTask.start(ROOM_ID, MEMBER, STARTED_AT);
        service = new ReleaseCleaningTaskService(
                new LockedCleaningTaskLoader(cleaningTaskRepository, roomRepository),
                cleaningTaskRepository,
                roomRepository,
                markPendingCleaningUseCase);
    }

    @Test
    @DisplayName("T008/T009: devuelve la habitación a PendingCleaning con el miembro como actor y cierra Released")
    void releaseTask_returnsRoomToPendingCleaning() {
        givenLockedTask();
        givenPendingCleaningAt(PENDING_AT);

        service.releaseTask(task.getId(), MEMBER);

        verify(markPendingCleaningUseCase)
                .markPendingCleaning(new MarkPendingCleaningCommand(ROOM_ID, SourceFlow.RELEASE_CLEANING_TASK, MEMBER));
        assertReleasedAt(PENDING_AT);
    }

    @Test
    @DisplayName("T008: la tarea de otro miembro se rechaza con TASK_NOT_OWNED sin transición")
    void releaseTask_notOwned() {
        givenLockedTask();

        assertThatThrownBy(() -> service.releaseTask(task.getId(), "otro")).isInstanceOf(TaskNotOwnedException.class);
        verifyNoInteractions(markPendingCleaningUseCase);
        verify(cleaningTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T014: sin tarea abierta, releaseOpenTaskOf no hace nada")
    void releaseOpenTaskOf_withoutOpenTask() {
        when(cleaningTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.empty());

        service.releaseOpenTaskOf(MEMBER, ADMIN);

        verifyNoInteractions(roomRepository, markPendingCleaningUseCase);
        verify(cleaningTaskRepository).findOpenByMember(MEMBER);
        verifyNoMoreInteractions(cleaningTaskRepository);
    }

    @Test
    @DisplayName("T014: con tarea abierta la libera con el Administrador como actor, sin validar el dueño")
    void releaseOpenTaskOf_releasesWithAdminAsActor() {
        when(cleaningTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.of(task));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));
        givenPendingCleaningAt(PENDING_AT);

        service.releaseOpenTaskOf(MEMBER, ADMIN);

        verify(roomRepository).lockById(ROOM_ID);
        verify(markPendingCleaningUseCase)
                .markPendingCleaning(new MarkPendingCleaningCommand(ROOM_ID, SourceFlow.RELEASE_CLEANING_TASK, ADMIN));
        assertReleasedAt(PENDING_AT);
    }

    @Test
    @DisplayName("T014: si al releerla tras el bloqueo ya no está abierta, no hace nada ni lanza excepción")
    void releaseOpenTaskOf_closedWhileWaitingForLock() {
        when(cleaningTaskRepository.findOpenByMember(MEMBER))
                .thenReturn(Optional.of(task))
                .thenReturn(Optional.empty());
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));

        service.releaseOpenTaskOf(MEMBER, ADMIN);

        verifyNoInteractions(markPendingCleaningUseCase);
        verify(cleaningTaskRepository, never()).save(any());
    }

    private void givenLockedTask() {
        when(cleaningTaskRepository.findRoomIdByTaskId(task.getId())).thenReturn(Optional.of(ROOM_ID));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));
        when(cleaningTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
    }

    private void givenPendingCleaningAt(OffsetDateTime pendingAt) {
        when(markPendingCleaningUseCase.markPendingCleaning(any()))
                .thenReturn(new MarkPendingCleaningResult(ROOM_ID, RoomStatus.InCleaning, pendingAt));
    }

    private void assertReleasedAt(OffsetDateTime endDateTime) {
        ArgumentCaptor<CleaningTask> captor = ArgumentCaptor.forClass(CleaningTask.class);
        verify(cleaningTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(CleaningTaskOutcome.Released);
        assertThat(captor.getValue().getEndDateTime()).isEqualTo(endDateTime);
    }

    private static Room room() {
        return new Room(
                ROOM_ID, "101", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), RoomStatus.InCleaning, null, 0);
    }
}
