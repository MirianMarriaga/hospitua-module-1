package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MarkPendingCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkPendingCleaningResult;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.exception.TaskNotFoundException;
import com.hospitua.habitaciones.domain.exception.TaskNotOwnedException;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.ReparationTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompleteRepairServiceTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String MEMBER = "mtorres";
    /** 2026-10-20 a las 10:00 en Colombia: la fecha de cierre es el 20. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Bogota"));

    private static final OffsetDateTime STARTED_AT = OffsetDateTime.parse("2026-10-20T08:00:00-05:00");
    /** Marca del periodo PendingCleaning, distinta de la hora del Clock para probar de dónde sale el fin. */
    private static final OffsetDateTime PENDING_AT = OffsetDateTime.parse("2026-10-20T10:00:03-05:00");

    private static final LocalDate CLOSING_DATE = LocalDate.of(2026, 10, 20);

    @Mock
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private TechnicalBlockReportRepositoryPort technicalBlockReportRepository;

    @Mock
    private DamageReportRepositoryPort damageReportRepository;

    @Mock
    private MarkPendingCleaningUseCase markPendingCleaningUseCase;

    @Mock
    private MaintenanceNoticePublisher maintenanceNoticePublisher;

    private CompleteRepairService service;

    @BeforeEach
    void setUp() {
        service = new CompleteRepairService(
                new LockedRepairTaskLoader(reparationTaskRepository, roomRepository),
                reparationTaskRepository,
                technicalBlockReportRepository,
                damageReportRepository,
                markPendingCleaningUseCase,
                maintenanceNoticePublisher,
                CLOCK);
    }

    @Test
    @DisplayName(
            "T012/T016: desde DisabledForRepairs pasa a PendingCleaning por CONFIRM_REPAIR_END, cierra Completed con"
                    + " la marca de esa transición y publica CANCELLED con el último reporte de daño")
    void complete_disabledForRepairs() {
        Room room = room(RoomStatus.DisabledForRepairs);
        ReparationTask task = givenLockedTask(room, MEMBER);
        givenPendingCleaning();
        DamageReport damage = DamageReport.report(ROOM_ID, "jramirez", "Grifo con fuga.", STARTED_AT.minusDays(1));
        when(damageReportRepository.findLatestByRoom(ROOM_ID)).thenReturn(Optional.of(damage));

        service.completeRepair(task.getId(), MEMBER);

        InOrder order = inOrder(roomRepository, markPendingCleaningUseCase, reparationTaskRepository);
        order.verify(roomRepository).lockById(ROOM_ID);
        order.verify(markPendingCleaningUseCase)
                .markPendingCleaning(new MarkPendingCleaningCommand(ROOM_ID, SourceFlow.CONFIRM_REPAIR_END, MEMBER));
        order.verify(reparationTaskRepository).save(task);
        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Completed);
        assertThat(task.getEndDateTime()).isEqualTo(PENDING_AT);
        verify(maintenanceNoticePublisher).publishCancelled(damage.getId(), room, CLOSING_DATE, CLOSING_DATE);
        verifyNoInteractions(technicalBlockReportRepository);
    }

    @Test
    @DisplayName("T013: desde TechnicalBlock completa el informe Applied y, si cierra en la fecha estimada, no avisa")
    void complete_technicalBlock_onEstimatedDate() {
        Room room = room(RoomStatus.TechnicalBlock);
        ReparationTask task = givenLockedTask(room, MEMBER);
        TechnicalBlockReport report = givenAppliedReport(CLOSING_DATE);
        givenPendingCleaning();

        service.completeRepair(task.getId(), MEMBER);

        assertThat(report.getStatus()).isEqualTo(TechnicalBlockStatus.Completed);
        verify(technicalBlockReportRepository).save(report);
        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Completed);
        verifyNoInteractions(maintenanceNoticePublisher, damageReportRepository);
    }

    @ParameterizedTest
    @ValueSource(ints = {-2, 3})
    @DisplayName("T013: desde TechnicalBlock, si cierra antes o después de la fecha estimada publica UPDATED con la"
            + " fecha de cierre y el rango original")
    void complete_technicalBlock_otherDate(int daysFromClosing) {
        Room room = room(RoomStatus.TechnicalBlock);
        ReparationTask task = givenLockedTask(room, MEMBER);
        LocalDate estimatedEnd = CLOSING_DATE.plusDays(daysFromClosing);
        TechnicalBlockReport report = givenAppliedReport(estimatedEnd);
        givenPendingCleaning();

        service.completeRepair(task.getId(), MEMBER);

        assertThat(report.getStatus()).isEqualTo(TechnicalBlockStatus.Completed);
        verify(maintenanceNoticePublisher)
                .publishUpdated(
                        report.getId(), room, report.getStartDate(), CLOSING_DATE, report.getStartDate(), estimatedEnd);
    }

    @Test
    @DisplayName("Una habitación TechnicalBlock sin informe Applied pasa a PendingCleaning igual, sin aviso")
    void complete_technicalBlockWithoutReport() {
        Room room = room(RoomStatus.TechnicalBlock);
        ReparationTask task = givenLockedTask(room, MEMBER);
        when(technicalBlockReportRepository.findAppliedByRoomIds(List.of(ROOM_ID)))
                .thenReturn(Map.of());
        givenPendingCleaning();

        service.completeRepair(task.getId(), MEMBER);

        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Completed);
        verify(technicalBlockReportRepository, never()).save(any());
        verifyNoInteractions(maintenanceNoticePublisher);
    }

    @Test
    @DisplayName("Una habitación DisabledForRepairs sin reporte de daño pasa a PendingCleaning igual, sin aviso")
    void complete_disabledWithoutDamageReport() {
        Room room = room(RoomStatus.DisabledForRepairs);
        ReparationTask task = givenLockedTask(room, MEMBER);
        givenPendingCleaning();
        when(damageReportRepository.findLatestByRoom(ROOM_ID)).thenReturn(Optional.empty());

        service.completeRepair(task.getId(), MEMBER);

        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Completed);
        verifyNoInteractions(maintenanceNoticePublisher);
    }

    @Test
    @DisplayName(
            "T014: la tarea de otro miembro es TASK_NOT_OWNED, también si ya está cerrada, sin tocar la habitación")
    void complete_notOwned() {
        Room room = room(RoomStatus.DisabledForRepairs);
        ReparationTask open = givenLockedTask(room, "otro");
        ReparationTask closed = givenLockedTask(room, "otro");
        closed.close(ReparationTaskOutcome.Released, STARTED_AT.plusHours(1));

        for (ReparationTask task : List.of(open, closed)) {
            assertThatThrownBy(() -> service.completeRepair(task.getId(), MEMBER))
                    .isInstanceOf(TaskNotOwnedException.class)
                    .hasMessage("Esta reparación la está haciendo otro compañero.");
        }
        verifyNoInteractions(markPendingCleaningUseCase, maintenanceNoticePublisher);
        verify(reparationTaskRepository, never()).save(any());
    }

    @Test
    @DisplayName("T014: una tarea propia ya cerrada es TASK_ALREADY_CLOSED, sin tocar la habitación")
    void complete_alreadyClosed() {
        ReparationTask task = givenLockedTask(room(RoomStatus.PendingCleaning), MEMBER);
        task.close(ReparationTaskOutcome.Completed, STARTED_AT.plusHours(1));

        assertThatThrownBy(() -> service.completeRepair(task.getId(), MEMBER))
                .isInstanceOf(TaskAlreadyClosedException.class)
                .hasMessage("Esta tarea ya fue cerrada.");
        verifyNoInteractions(markPendingCleaningUseCase, maintenanceNoticePublisher);
    }

    @Test
    @DisplayName("Una tarea que no existe es TASK_NOT_FOUND, sin bloquear ninguna habitación")
    void complete_notFound() {
        UUID taskId = UUID.randomUUID();
        when(reparationTaskRepository.findRoomIdByTaskId(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeRepair(taskId, MEMBER))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessage("No encontramos esta reparación. Vuelve al panel.");
        verifyNoInteractions(roomRepository, markPendingCleaningUseCase);
    }

    private ReparationTask givenLockedTask(Room room, String owner) {
        ReparationTask task = ReparationTask.start(ROOM_ID, owner, STARTED_AT);
        when(reparationTaskRepository.findRoomIdByTaskId(task.getId())).thenReturn(Optional.of(ROOM_ID));
        when(roomRepository.lockById(ROOM_ID)).thenReturn(Optional.of(room));
        when(reparationTaskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        return task;
    }

    private TechnicalBlockReport givenAppliedReport(LocalDate estimatedEnd) {
        TechnicalBlockReport report = TechnicalBlockReport.schedule(
                ROOM_ID,
                MEMBER,
                "Cambio del aire acondicionado.",
                CLOSING_DATE.minusDays(5),
                estimatedEnd,
                STARTED_AT.minusDays(10));
        report.apply();
        when(technicalBlockReportRepository.findAppliedByRoomIds(List.of(ROOM_ID)))
                .thenReturn(Map.of(ROOM_ID, report));
        return report;
    }

    private void givenPendingCleaning() {
        when(markPendingCleaningUseCase.markPendingCleaning(any()))
                .thenAnswer(invocation ->
                        new MarkPendingCleaningResult(ROOM_ID, RoomStatus.DisabledForRepairs, PENDING_AT));
    }

    private static Room room(RoomStatus status) {
        return new Room(ROOM_ID, "305", 3, RoomCategory.DOBLE, 2, new BigDecimal("180000"), status, null, 0);
    }
}
