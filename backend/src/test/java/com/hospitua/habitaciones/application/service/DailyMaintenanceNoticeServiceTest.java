package com.hospitua.habitaciones.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyMaintenanceNoticeServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-01T10:00:00-05:00");

    @Mock
    private RoomRepositoryPort roomRepository;

    @Mock
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Mock
    private DamageReportRepositoryPort damageReportRepository;

    @Mock
    private MaintenanceNoticePublisher publisher;

    private DailyMaintenanceNoticeService service;

    @BeforeEach
    void setUp() {
        service =
                new DailyMaintenanceNoticeService(roomRepository, reportRepository, damageReportRepository, publisher);
    }

    @Test
    @DisplayName("T025: avisa hoy por cada DisabledForRepairs (id del último daño) y cada Applied vencido, no por el"
            + " vigente ni por la reparación o el bloqueo sin su informe")
    void publishesOnlyForRepairsAndOverdueBlocks() {
        Room repair = room(RoomStatus.DisabledForRepairs);
        Room repairWithoutReport = room(RoomStatus.DisabledForRepairs);
        Room overdue = room(RoomStatus.TechnicalBlock);
        Room current = room(RoomStatus.TechnicalBlock);
        Room blockWithoutReport = room(RoomStatus.TechnicalBlock);
        when(roomRepository.findByStatusIn(EnumSet.of(RoomStatus.DisabledForRepairs, RoomStatus.TechnicalBlock)))
                .thenReturn(List.of(repair, overdue, repairWithoutReport, current, blockWithoutReport));
        DamageReport damage = DamageReport.report(repair.getId(), "mtorres", "Ventana fisurada.", NOW);
        when(damageReportRepository.findLatestByRoomIds(List.of(repair.getId(), repairWithoutReport.getId())))
                .thenReturn(Map.of(repair.getId(), damage));
        TechnicalBlockReport overdueReport = applied(overdue, TODAY.minusDays(1));
        TechnicalBlockReport currentReport = applied(current, TODAY);
        when(reportRepository.findAppliedByRoomIds(
                        List.of(overdue.getId(), current.getId(), blockWithoutReport.getId())))
                .thenReturn(Map.of(overdue.getId(), overdueReport, current.getId(), currentReport));

        service.publishDaily(TODAY);

        verify(publisher).publishScheduled(damage.getId(), repair, TODAY, TODAY);
        verify(publisher).publishScheduled(overdueReport.getId(), overdue, TODAY, TODAY);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    @DisplayName("T025: sin habitaciones en reparación ni bloqueadas no se publica nada")
    void noRooms_noNotices() {
        when(roomRepository.findByStatusIn(any())).thenReturn(List.of());
        when(damageReportRepository.findLatestByRoomIds(anyCollection())).thenReturn(Map.of());
        when(reportRepository.findAppliedByRoomIds(anyCollection())).thenReturn(Map.of());

        service.publishDaily(TODAY);

        verify(publisher, never()).publishScheduled(any(), any(), any(), any());
    }

    private static TechnicalBlockReport applied(Room room, LocalDate end) {
        TechnicalBlockReport report =
                TechnicalBlockReport.schedule(room.getId(), "lperez", "Cambio de tubería.", end.minusDays(3), end, NOW);
        report.apply();
        return report;
    }

    private static Room room(RoomStatus status) {
        return new Room(
                UUID.randomUUID(),
                "1" + status.ordinal(),
                1,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("150000"),
                status,
                null,
                0);
    }
}
