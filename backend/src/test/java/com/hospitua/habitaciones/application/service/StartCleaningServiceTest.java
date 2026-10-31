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

import com.hospitua.habitaciones.application.dto.CleaningTaskStartedDto;
import com.hospitua.habitaciones.application.dto.StartCleaningCommand;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomNotFoundException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StartCleaningServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "jramirez";
    private static final OffsetDateTime PERIOD_START = OffsetDateTime.parse("2026-10-08T09:15:00-05:00");

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Mock
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    private StartCleaningService service;

    @BeforeEach
    void setUp() {
        service = new StartCleaningService(roomRepository, cleaningTaskRepository, transitionRoomStateUseCase);
    }

    @ParameterizedTest
    @EnumSource(
            value = RoomStatus.class,
            names = {"PendingCleaning", "Available"})
    @DisplayName("T005/T006: desde PendingCleaning o Available pasa a InCleaning por START_CLEANING y crea la tarea")
    void startCleaning_createsTaskAndTransitions(RoomStatus status) {
        givenRoom(status);
        when(transitionRoomStateUseCase.transition(
                        ROOM_ID, RoomStatus.InCleaning, MEMBER, SourceFlow.START_CLEANING, null))
                .thenReturn(PERIOD_START);
        when(cleaningTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CleaningTaskStartedDto started = service.startCleaning(new StartCleaningCommand(ROOM_ID, MEMBER));

        ArgumentCaptor<CleaningTask> task = ArgumentCaptor.forClass(CleaningTask.class);
        InOrder order = inOrder(roomRepository, cleaningTaskRepository, transitionRoomStateUseCase);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(cleaningTaskRepository).findOpenByMember(MEMBER);
        order.verify(transitionRoomStateUseCase)
                .transition(ROOM_ID, RoomStatus.InCleaning, MEMBER, SourceFlow.START_CLEANING, null);
        order.verify(cleaningTaskRepository).save(task.capture());
        // La tarea abierta de la habitación solo la detecta el índice único al guardar. StartCleaningIntegrationTest
        // (T011) usa ese fallo para probar que la transición y el historial se revierten: si se agrega otra consulta
        // de tareas antes de la transición, esta línea falla y hay que rehacer esa prueba
        verifyNoMoreInteractions(cleaningTaskRepository);
        assertThat(task.getValue().getRoomId()).isEqualTo(ROOM_ID);
        assertThat(task.getValue().getCleaningStaffMemberId()).isEqualTo(MEMBER);
        assertThat(task.getValue().isOpen()).isTrue();
        assertThat(started.taskId()).isEqualTo(task.getValue().getId());
    }

    @Test
    @DisplayName("T009: la marca de inicio de la tarea es la del periodo que abre la transición, no la del cliente")
    void startCleaning_usesTransitionTimestamp() {
        givenRoom(RoomStatus.PendingCleaning);
        when(transitionRoomStateUseCase.transition(any(), any(), any(), any(), any()))
                .thenReturn(PERIOD_START);
        when(cleaningTaskRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.startCleaning(new StartCleaningCommand(ROOM_ID, MEMBER));

        ArgumentCaptor<CleaningTask> task = ArgumentCaptor.forClass(CleaningTask.class);
        verify(cleaningTaskRepository).save(task.capture());
        assertThat(task.getValue().getStartDateTime()).isEqualTo(PERIOD_START);
    }

    @Test
    @DisplayName("T007: desde otro estado es ROOM_INVALID_STATE con el estado actual y el mensaje del plan, sin tarea")
    void startCleaning_invalidState() {
        givenRoom(RoomStatus.Occupied);
        when(transitionRoomStateUseCase.transition(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidRoomTransitionException(RoomStatus.Occupied));

        assertThatThrownBy(() -> service.startCleaning(new StartCleaningCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(InvalidRoomTransitionException.class)
                .hasMessage("La habitación está Ocupada; ahora no se puede limpiar.")
                .extracting(e -> ((InvalidRoomTransitionException) e).details().get("currentStatus"))
                .isEqualTo("Occupied");
        verify(cleaningTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T008: si el miembro ya tiene una tarea abierta es ACTIVE_TASK_EXISTS y no cambia la habitación")
    void startCleaning_memberWithOpenTask() {
        givenRoom(RoomStatus.PendingCleaning);
        when(cleaningTaskRepository.findOpenByMember(MEMBER))
                .thenReturn(Optional.of(CleaningTask.start(UUID.randomUUID(), MEMBER, PERIOD_START)));

        assertThatThrownBy(() -> service.startCleaning(new StartCleaningCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(ActiveTaskExistsException.class)
                .hasMessage("Ya estás limpiando otra habitación. Termínala o déjala antes de empezar otra.");
        verifyNoInteractions(transitionRoomStateUseCase);
        verify(cleaningTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("Una habitación que no existe es ROOM_NOT_FOUND, antes de mirar las tareas del miembro")
    void startCleaning_unknownRoom() {
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startCleaning(new StartCleaningCommand(ROOM_ID, MEMBER)))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(cleaningTaskRepository, transitionRoomStateUseCase);
    }

    private void givenRoom(RoomStatus status) {
        when(roomRepository.lockById(ROOM_ID))
                .thenReturn(Optional.of(
                        new Room(ROOM_ID, "305", 3, RoomCategory.DOBLE, 2, new BigDecimal("180000"), status, null, 0)));
    }
}
