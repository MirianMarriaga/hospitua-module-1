package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.RepairTaskStartedDto;
import com.hospitua.habitaciones.application.dto.StartRepairCommand;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
class StartRepairServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "mtorres";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T18:00:00Z"), ZoneId.of("America/Bogota"));

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private ReparationTaskRepositoryPort reparationTaskRepository;

    private StartRepairService service;

    @BeforeEach
    void setUp() {
        service = new StartRepairService(roomRepository, reparationTaskRepository, CLOCK);
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"DisabledForRepairs", "TechnicalBlock"})
    @DisplayName("T005: desde DisabledForRepairs o TechnicalBlock crea la tarea con la hora del servidor, sin tocar"
            + " la habitación")
    void startRepair_createsTask(RoomStatus status) {
        givenRoom(status);
        when(reparationTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RepairTaskStartedDto started = service.startRepair(new StartRepairCommand(ROOM_ID, MEMBER));

        ArgumentCaptor<ReparationTask> task = ArgumentCaptor.forClass(ReparationTask.class);
        InOrder order = inOrder(roomRepository, reparationTaskRepository);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(reparationTaskRepository).findOpenByMember(MEMBER);
        order.verify(reparationTaskRepository).existsOpenByRoom(ROOM_ID);
        order.verify(reparationTaskRepository).save(task.capture());
        // Sin transición ni historial: el servicio no guarda la habitación (FR-015)
        verifyNoMoreInteractions(roomRepository);
        assertThat(task.getValue().getRoomId()).isEqualTo(ROOM_ID);
        assertThat(task.getValue().getMaintenanceStaffMemberId()).isEqualTo(MEMBER);
        assertThat(task.getValue().isOpen()).isTrue();
        assertThat(task.getValue().getStartDateTime()).isEqualTo(OffsetDateTime.now(CLOCK));
        assertThat(started.taskId()).isEqualTo(task.getValue().getId());
    }

    @Test
    @DisplayName("T006: si la habitación tiene una tarea abierta de otro miembro es ROOM_TASK_EXISTS, sin tarea nueva")
    void startRepair_roomWithOpenTask() {
        givenRoom(RoomStatus.DisabledForRepairs);
        when(reparationTaskRepository.existsOpenByRoom(ROOM_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.startRepair(new StartRepairCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(RoomTaskExistsException.class)
                .hasMessage("Otro compañero ya está reparando esta habitación.");
        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T007: si el miembro ya tiene una tarea abierta es ACTIVE_TASK_EXISTS, antes de mirar la habitación")
    void startRepair_memberWithOpenTask() {
        givenRoom(RoomStatus.TechnicalBlock);
        when(reparationTaskRepository.findOpenByMember(MEMBER))
                .thenReturn(Optional.of(ReparationTask.start(UUID.randomUUID(), MEMBER, OffsetDateTime.now(CLOCK))));

        assertThatThrownBy(() -> service.startRepair(new StartRepairCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(ActiveTaskExistsException.class)
                .hasMessage("Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra.");
        verify(reparationTaskRepository, never()).existsOpenByRoom(any());
        verify(reparationTaskRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"DisabledForRepairs", "TechnicalBlock"},
            mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("T008: en cualquier otro estado es ROOM_INVALID_STATE con el estado actual, sin tarea")
    void startRepair_invalidState(RoomStatus status) {
        givenRoom(status);

        assertThatThrownBy(() -> service.startRepair(new StartRepairCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .hasMessage("La habitación está " + status.displayName() + "; no necesita reparación.")
                .extracting(e -> ((InvalidRoomTransitionException) e).details().get("currentStatus"))
                .isEqualTo(status.name());
        verify(reparationTaskRepository, never()).existsOpenByRoom(any());
        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una habitación que no existe es ROOM_NOT_FOUND, antes de mirar las tareas")
    void startRepair_unknownRoom() {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startRepair(new StartRepairCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(reparationTaskRepository);
    }

    private void givenRoom(RoomStatus status) {
        when(roomRepository.lockById(ROOM_ID))
                .thenReturn(Optional.of(
                        new Room(ROOM_ID, "305", 3, RoomCategory.DOBLE, 2, new BigDecimal("180000"), status, null, 0)));
    }
}
