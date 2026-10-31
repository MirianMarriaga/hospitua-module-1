package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.CompleteCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableResult;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedResult;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.in.ReportDamageUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.exception.TaskNotFoundException;
import com.hospitua.habitaciones.domain.exception.TaskNotOwnedException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.MarkRoomReservedOutcome;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompleteCleaningServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "jramirez";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-10-08T09:15:00-05:00");
    private static final OffsetDateTime AVAILABLE_AT = OffsetDateTime.parse("2026-10-08T10:05:00-05:00");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);

    @Mock
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private DailyReservationRepositoryPort dailyReservationRepository;

    @Mock
    private MarkRoomAvailableUseCase markRoomAvailableUseCase;

    @Mock
    private MarkRoomReservedUseCase markRoomReservedUseCase;

    @Mock
    private ReportDamageUseCase reportDamageUseCase;

    private CleaningTask task;
    private CompleteCleaningService service;

    @BeforeEach
    void setUp() {
        task = CleaningTask.start(ROOM_ID, MEMBER, STARTED_AT);
        service = serviceWith(Clock.fixed(AVAILABLE_AT.toInstant(), BOGOTA));
    }

    @Test
    @DisplayName("T004: sin daño ni llegada pendiente queda Available y la tarea se cierra Completed")
    void complete_withoutArrival() {
        givenLockedTask();
        givenAvailableAt(AVAILABLE_AT);
        when(dailyReservationRepository.findPendingArrivalReservationRef(ROOM_ID, TODAY))
                .thenReturn(Optional.empty());

        service.completeCleaning(new CompleteCleaningCommand(task.getId(), MEMBER, null));

        InOrder order = inOrder(cleaningTaskRepository, roomRepository, markRoomAvailableUseCase);
        order.verify(cleaningTaskRepository).findRoomIdByTaskId(task.getId());
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(cleaningTaskRepository).findById(task.getId());
        order.verify(markRoomAvailableUseCase)
                .markAvailable(new MarkRoomAvailableCommand(ROOM_ID, SourceFlow.CONFIRM_CLEANING_END, MEMBER, null));
        assertClosed(CleaningTaskOutcome.Completed);
        verifyNoInteractions(markRoomReservedUseCase, reportDamageUseCase);
    }

    @Test
    @DisplayName("T006: con una llegada pendiente hoy queda Reserved para esa reserva por CLEANING_END")
    void complete_withPendingArrival() {
        givenLockedTask();
        givenAvailableAt(AVAILABLE_AT);
        when(dailyReservationRepository.findPendingArrivalReservationRef(ROOM_ID, TODAY))
                .thenReturn(Optional.of("RSV-8D02E5A4"));
        when(markRoomReservedUseCase.markReserved(any()))
                .thenReturn(new MarkRoomReservedResult(
                        MarkRoomReservedOutcome.RESERVED, RoomStatus.Reserved, "RSV-8D02E5A4"));

        service.completeCleaning(new CompleteCleaningCommand(task.getId(), MEMBER, "   "));

        verify(markRoomReservedUseCase)
                .markReserved(new MarkRoomReservedCommand(ROOM_ID, "RSV-8D02E5A4", ReservationTrigger.CLEANING_END));
        assertClosed(CleaningTaskOutcome.Completed);
        verifyNoInteractions(reportDamageUseCase);
    }

    @Test
    @DisplayName("T007: con daño lo reporta por CONFIRM_CLEANING_END, cierra DamageReported y no consulta llegadas")
    void complete_withDamage() {
        givenLockedTask();
        givenAvailableAt(AVAILABLE_AT);

        service.completeCleaning(new CompleteCleaningCommand(task.getId(), MEMBER, "Grifo con fuga."));

        verify(reportDamageUseCase)
                .reportDamage(
                        new ReportDamageCommand(ROOM_ID, "Grifo con fuga.", MEMBER, SourceFlow.CONFIRM_CLEANING_END));
        assertClosed(CleaningTaskOutcome.DamageReported);
        verifyNoInteractions(dailyReservationRepository, markRoomReservedUseCase);
    }

    @Test
    @DisplayName("T005: la tarea de otro miembro se rechaza con TASK_NOT_OWNED sin transiciones")
    void complete_notOwned() {
        givenLockedTask();

        assertThatThrownBy(() -> service.completeCleaning(new CompleteCleaningCommand(task.getId(), "otro", null)))
                .isInstanceOf(TaskNotOwnedException.class)
                .hasMessage("Esta habitación la está limpiando otro compañero.");
        assertNoTransitions();
    }

    @Test
    @DisplayName("FR-016: una tarea ya cerrada se rechaza con TASK_ALREADY_CLOSED sin transiciones")
    void complete_alreadyClosed() {
        task.close(CleaningTaskOutcome.Released, AVAILABLE_AT);
        givenLockedTask();

        assertThatThrownBy(() -> service.completeCleaning(new CompleteCleaningCommand(task.getId(), MEMBER, null)))
                .isInstanceOf(TaskAlreadyClosedException.class);
        assertNoTransitions();
    }

    @Test
    @DisplayName("Una tarea inexistente responde TASK_NOT_FOUND sin bloquear ninguna habitación")
    void complete_taskNotFound() {
        UUID taskId = UUID.randomUUID();
        when(cleaningTaskRepository.findRoomIdByTaskId(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeCleaning(new CompleteCleaningCommand(taskId, MEMBER, null)))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("No encontramos esta limpieza. Vuelve al panel.");
        verifyNoInteractions(roomRepository);
        assertNoTransitions();
    }

    @Test
    @DisplayName("T009: endDateTime es la marca de Available, y la llegada se busca en la fecha de Bogotá")
    void complete_endDateTimeIsAvailableTransition() {
        OffsetDateTime availableAtUtc = OffsetDateTime.parse("2026-10-09T03:30:00Z");
        service = serviceWith(Clock.fixed(availableAtUtc.toInstant(), BOGOTA));
        givenLockedTask();
        givenAvailableAt(availableAtUtc);
        when(dailyReservationRepository.findPendingArrivalReservationRef(ROOM_ID, TODAY))
                .thenReturn(Optional.empty());

        service.completeCleaning(new CompleteCleaningCommand(task.getId(), MEMBER, null));

        assertThat(savedTask().getEndDateTime()).isEqualTo(availableAtUtc);
    }

    @Test
    @DisplayName("FR-018: una descripción vacía o solo con espacios equivale a no reportar daño")
    void command_blankDescriptionIsNoDamage() {
        assertThat(new CompleteCleaningCommand(UUID.randomUUID(), MEMBER, " \t").reportsDamage())
                .isFalse();
        assertThat(new CompleteCleaningCommand(UUID.randomUUID(), MEMBER, null).damageDescription())
                .isNull();
    }

    private void givenLockedTask() {
        when(cleaningTaskRepository.findRoomIdByTaskId(task.getId())).thenReturn(Optional.of(ROOM_ID));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room()));
        when(cleaningTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
    }

    private void givenAvailableAt(OffsetDateTime availableAt) {
        when(markRoomAvailableUseCase.markAvailable(any()))
                .thenReturn(new MarkRoomAvailableResult(ROOM_ID, RoomStatus.InCleaning, availableAt));
    }

    private void assertClosed(CleaningTaskOutcome outcome) {
        CleaningTask saved = savedTask();
        assertThat(saved.isOpen()).isFalse();
        assertThat(saved.getOutcome()).isEqualTo(outcome);
        assertThat(saved.getEndDateTime()).isEqualTo(AVAILABLE_AT);
    }

    private CleaningTask savedTask() {
        ArgumentCaptor<CleaningTask> captor = ArgumentCaptor.forClass(CleaningTask.class);
        verify(cleaningTaskRepository).save(captor.capture());
        return captor.getValue();
    }

    private void assertNoTransitions() {
        verifyNoInteractions(markRoomAvailableUseCase, markRoomReservedUseCase, reportDamageUseCase);
        verify(cleaningTaskRepository, never()).save(any());
    }

    private CompleteCleaningService serviceWith(Clock clock) {
        return new CompleteCleaningService(
                new LockedCleaningTaskLoader(cleaningTaskRepository, roomRepository),
                cleaningTaskRepository,
                dailyReservationRepository,
                markRoomAvailableUseCase,
                markRoomReservedUseCase,
                reportDamageUseCase,
                clock);
    }

    private static Room room() {
        return new Room(
                ROOM_ID, "101", 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), RoomStatus.InCleaning, null, 0);
    }
}
