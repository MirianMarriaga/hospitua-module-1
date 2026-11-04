package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.exception.TaskNotOwnedException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.ReparationTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * El servicio no depende de la transición de estados, del historial, de los informes ni del publicador de avisos: liberar
 * no puede cambiar la habitación ni avisar a Módulo 2 (T015, FR-016, FR-019). Aquí se prueba que tampoco guarda la
 * habitación.
 */
@ExtendWith(MockitoExtension.class)
class ReleaseRepairTaskServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "mtorres";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-10-20T08:00:00-05:00");

    @Mock
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    private ReleaseRepairTaskService service;

    @BeforeEach
    void setUp() {
        service = new ReleaseRepairTaskService(
                new LockedRepairTaskLoader(reparationTaskRepository, roomRepository),
                reparationTaskRepository,
                roomRepository,
                CLOCK);
    }

    @Test
    @DisplayName("T015/T016: cierra Released a la hora del Clock con la habitación bloqueada, sin guardarla")
    void releaseTask_closesReleased() {
        ReparationTask task = givenLockedTask(MEMBER);

        service.releaseTask(task.getId(), MEMBER);

        assertReleased(task);
        verify(roomRepository).lockById(ROOM_ID);
        verifyNoMoreInteractions(roomRepository);
    }

    @Test
    @DisplayName("T014: la tarea de otro miembro es TASK_NOT_OWNED, también si ya está cerrada")
    void releaseTask_notOwned() {
        ReparationTask open = givenLockedTask("otro");
        ReparationTask closed = givenLockedTask("otro");
        closed.close(ReparationTaskOutcome.Completed, STARTED_AT.plusHours(1));

        for (ReparationTask task : List.of(open, closed)) {
            assertThatThrownBy(() -> service.releaseTask(task.getId(), MEMBER))
                    .isInstanceOf(TaskNotOwnedException.class);
        }
        assertThat(open.isOpen()).isTrue();
        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T014: una tarea propia ya cerrada es TASK_ALREADY_CLOSED")
    void releaseTask_alreadyClosed() {
        ReparationTask task = givenLockedTask(MEMBER);
        task.close(ReparationTaskOutcome.Completed, STARTED_AT.plusHours(1));

        assertThatThrownBy(() -> service.releaseTask(task.getId(), MEMBER))
                .isInstanceOf(TaskAlreadyClosedException.class);
        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T020: releaseOpenTaskOf sin tarea abierta no hace nada")
    void releaseOpenTaskOf_withoutTask() {
        when(reparationTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.empty());

        service.releaseOpenTaskOf(MEMBER);

        verifyNoInteractions(roomRepository);
        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T020: releaseOpenTaskOf bloquea la habitación, relee la tarea y la cierra Released sin validar dueño")
    void releaseOpenTaskOf_releases() {
        ReparationTask task = ReparationTask.start(ROOM_ID, MEMBER, STARTED_AT);
        when(reparationTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.of(task));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));

        service.releaseOpenTaskOf(MEMBER);

        assertReleased(task);
        verify(reparationTaskRepository, never()).findById(any());
        verifyNoMoreInteractions(roomRepository);
    }

    @Test
    @DisplayName("T020: si al releer tras el bloqueo la tarea ya se cerró, no hace nada ni lanza excepción")
    void releaseOpenTaskOf_closedWhileWaiting() {
        ReparationTask task = ReparationTask.start(ROOM_ID, MEMBER, STARTED_AT);
        when(reparationTaskRepository.findOpenByMember(MEMBER))
                .thenReturn(Optional.of(task))
                .thenReturn(Optional.empty());
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));

        service.releaseOpenTaskOf(MEMBER);

        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T020: si al releer la tarea abierta es otra (en otra habitación, sin bloquear), no la toca")
    void releaseOpenTaskOf_otherTaskWhileWaiting() {
        ReparationTask first = ReparationTask.start(ROOM_ID, MEMBER, STARTED_AT);
        ReparationTask other = ReparationTask.start(UUID.randomUUID(), MEMBER, STARTED_AT.plusHours(1));
        when(reparationTaskRepository.findOpenByMember(MEMBER))
                .thenReturn(Optional.of(first))
                .thenReturn(Optional.of(other));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));

        service.releaseOpenTaskOf(MEMBER);

        assertThat(other.isOpen()).isTrue();
        verify(reparationTaskRepository, never()).save(any());
    }

    private ReparationTask givenLockedTask(String owner) {
        ReparationTask task = ReparationTask.start(ROOM_ID, owner, STARTED_AT);
        when(reparationTaskRepository.findRoomIdByTaskId(task.getId())).thenReturn(Optional.of(ROOM_ID));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));
        when(reparationTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        return task;
    }

    private void assertReleased(ReparationTask task) {
        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Released);
        assertThat(task.getEndDateTime()).isEqualTo(OffsetDateTime.now(CLOCK));
        verify(reparationTaskRepository).save(task);
    }

    private static Room room() {
        return new Room(
                ROOM_ID, "305", 3, RoomCategory.DOBLE, 2, new BigDecimal("180000"), RoomStatus.TechnicalBlock, null, 0);
    }
}
