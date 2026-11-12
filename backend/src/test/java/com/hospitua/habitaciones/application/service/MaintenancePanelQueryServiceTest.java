package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.dto.MaintenancePanelDto;
import com.hospitua.habitaciones.application.dto.MaintenancePanelDto.MaintenanceRangeDto;
import com.hospitua.habitaciones.application.dto.MaintenancePanelDto.MaintenanceRoomDto;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MaintenancePanelQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneId.of("America/Bogota"));
    private static final OffsetDateTime SCHEDULED_AT = OffsetDateTime.parse("2026-10-01T10:00:00-05:00");
    private static final String MEMBER = "mtorres";

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Mock
    private TechnicalBlockReportRepositoryPort reportRepository;

    private MaintenancePanelQueryService service;

    @BeforeEach
    void setUp() {
        service = new MaintenancePanelQueryService(roomRepository, reparationTaskRepository, reportRepository, CLOCK);
    }

    @Test
    @DisplayName("T003: sin Inactive (lo excluye el puerto), en tres grupos que conservan el orden por número")
    void threeGroups_stableByNumber() {
        Room available101 = room("101", RoomStatus.Available);
        Room repair102 = room("102", RoomStatus.DisabledForRepairs);
        Room blockCurrent103 = room("103", RoomStatus.TechnicalBlock);
        Room blockOverdue104 = room("104", RoomStatus.TechnicalBlock);
        Room occupied105 = room("105", RoomStatus.Occupied);
        Room repair106 = room("106", RoomStatus.DisabledForRepairs);
        Room blockOverdue107 = room("107", RoomStatus.TechnicalBlock);
        givenRooms(available101, repair102, blockCurrent103, blockOverdue104, occupied105, repair106, blockOverdue107);
        givenApplied(Map.of(
                blockCurrent103.getId(), applied(blockCurrent103, TODAY),
                blockOverdue104.getId(), applied(blockOverdue104, TODAY.minusDays(1)),
                blockOverdue107.getId(), applied(blockOverdue107, TODAY.minusDays(3))));
        givenScheduled(Map.of());
        givenOpenTasks(Set.of());

        MaintenancePanelDto panel = service.getPanel(MEMBER);

        assertThat(panel.activeTask()).isNull();
        assertThat(panel.rooms())
                .extracting(MaintenanceRoomDto::roomNumber)
                .containsExactly("104", "107", "102", "103", "106", "101", "105");
        verify(roomRepository).findByStatusNot(RoomStatus.Inactive);
    }

    @Test
    @DisplayName("T004: overdue según isOverdue(today); maintenance toma el Applied o el primer Scheduled; sin informes"
            + " vigentes es nulo; hasOpenTask con la tarea de otro miembro")
    void rowFields() {
        Room overdue = room("201", RoomStatus.TechnicalBlock);
        Room current = room("202", RoomStatus.TechnicalBlock);
        Room occupiedScheduled = room("203", RoomStatus.Occupied);
        Room withOpenTask = room("204", RoomStatus.DisabledForRepairs);
        Room nothing = room("205", RoomStatus.Available);
        givenRooms(overdue, current, occupiedScheduled, withOpenTask, nothing);
        givenApplied(Map.of(
                overdue.getId(), applied(overdue, TODAY.minusDays(1)),
                current.getId(), applied(current, TODAY)));
        // El repositorio solo devuelve Scheduled: un informe Expired nunca llega aquí
        givenScheduled(Map.of(
                current.getId(), report(current, TODAY.plusDays(10), TODAY.plusDays(12)),
                occupiedScheduled.getId(), report(occupiedScheduled, TODAY.plusDays(5), TODAY.plusDays(6))));
        givenOpenTasks(Set.of(withOpenTask.getId()));

        Map<String, MaintenanceRoomDto> rows = byNumber(service.getPanel(MEMBER));

        assertThat(rows.get("201").overdue()).isTrue();
        assertThat(rows.get("201").maintenance())
                .isEqualTo(new MaintenanceRangeDto(TODAY.minusDays(3), TODAY.minusDays(1)));
        assertThat(rows.get("202").overdue()).isFalse();
        assertThat(rows.get("202").maintenance()).isEqualTo(new MaintenanceRangeDto(TODAY.minusDays(2), TODAY));
        assertThat(rows.get("203").maintenance())
                .isEqualTo(new MaintenanceRangeDto(TODAY.plusDays(5), TODAY.plusDays(6)));
        assertThat(rows.get("203").overdue()).isFalse();
        assertThat(rows.get("204").hasOpenTask()).isTrue();
        assertThat(rows.get("204").maintenance()).isNull();
        assertThat(rows.get("205"))
                .isEqualTo(new MaintenanceRoomDto(
                        nothing.getId(), "205", RoomCategory.DOBLE, RoomStatus.Available, false, false, null));
    }

    @Test
    @DisplayName(
            "T005: con tarea abierta devuelve activeTask con taskId, su inicio y la fila de su habitación, y rooms vacío")
    void activeTask() {
        Room room = room("301", RoomStatus.TechnicalBlock);
        ReparationTask task = ReparationTask.start(room.getId(), MEMBER, SCHEDULED_AT);
        when(reparationTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.of(task));
        when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        givenApplied(Map.of(room.getId(), applied(room, TODAY.minusDays(1))));
        givenScheduled(Map.of());

        MaintenancePanelDto panel = service.getPanel(MEMBER);

        assertThat(panel.rooms()).isEmpty();
        assertThat(panel.activeTask().taskId()).isEqualTo(task.getId());
        assertThat(panel.activeTask().startDateTime()).isEqualTo(task.getStartDateTime());
        assertThat(panel.activeTask().room())
                .isEqualTo(new MaintenanceRoomDto(
                        room.getId(),
                        "301",
                        RoomCategory.DOBLE,
                        RoomStatus.TechnicalBlock,
                        true,
                        true,
                        new MaintenanceRangeDto(TODAY.minusDays(3), TODAY.minusDays(1))));
        verify(roomRepository, never()).findByStatusNot(RoomStatus.Inactive);
    }

    @Test
    @DisplayName("FR-008: sin habitaciones responde rooms vacío")
    void emptyPanel() {
        givenRooms();
        givenApplied(Map.of());
        givenScheduled(Map.of());
        givenOpenTasks(Set.of());

        assertThat(service.getPanel(MEMBER).rooms()).isEmpty();
    }

    private void givenRooms(Room... rooms) {
        when(reparationTaskRepository.findOpenByMember(MEMBER)).thenReturn(Optional.empty());
        when(roomRepository.findByStatusNot(RoomStatus.Inactive)).thenReturn(List.of(rooms));
    }

    private void givenApplied(Map<UUID, TechnicalBlockReport> applied) {
        when(reportRepository.findAppliedByRoomIds(anyCollection())).thenReturn(applied);
    }

    private void givenScheduled(Map<UUID, TechnicalBlockReport> scheduled) {
        when(reportRepository.findEarliestScheduledByRoomIds(anyCollection())).thenReturn(scheduled);
    }

    private void givenOpenTasks(Set<UUID> roomIds) {
        when(reparationTaskRepository.findRoomIdsWithOpenTask()).thenReturn(roomIds);
    }

    private static Map<String, MaintenanceRoomDto> byNumber(MaintenancePanelDto panel) {
        return panel.rooms().stream().collect(Collectors.toMap(MaintenanceRoomDto::roomNumber, row -> row));
    }

    /** Applied de tres días que termina en {@code end}. */
    private static TechnicalBlockReport applied(Room room, LocalDate end) {
        TechnicalBlockReport report = report(room, end.minusDays(2), end);
        report.apply();
        return report;
    }

    private static TechnicalBlockReport report(Room room, LocalDate start, LocalDate end) {
        return TechnicalBlockReport.schedule(room.getId(), "lperez", "Cambio de tubería.", start, end, SCHEDULED_AT);
    }

    private static Room room(String number, RoomStatus status) {
        return new Room(UUID.randomUUID(), number, 1, RoomCategory.DOBLE, 2, new BigDecimal("150000"), status, null, 0);
    }
}
