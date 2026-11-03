package com.hospitua.habitaciones.infrastructure.adapters.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import com.hospitua.habitaciones.application.ports.in.ApplyScheduledTechnicalBlocksUseCase;
import com.hospitua.habitaciones.application.ports.in.IngestDailyReservationsUseCase;
import com.hospitua.habitaciones.application.ports.in.PublishDailyMaintenanceNoticesUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationIngestionOutcome;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Trabajo diario de mantenimiento sobre la aplicación completa y PostgreSQL real
 * (plan-programar-bloqueo-tecnico-para-habitacion.md T016 y T019). La ingesta se invoca por su puerto con el día
 * operativo de hoy, y el listener de {@link TechnicalBlockScheduler} corre tras su commit en el mismo hilo. El espía de
 * {@code runDaily} le da a esta clase su propio contexto y su propia base, sin las listas de otros días que dejan las
 * pruebas de la cola (una lista de hoy anterior a esas se descartaría como atrasada).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TechnicalBlockSchedulerIntegrationTest {

    private static final String REASON = "Cambio preventivo del sistema de aire acondicionado.";
    private static final String MEMBER = "lperez";

    @Autowired
    private IngestDailyReservationsUseCase ingestDailyReservations;

    @Autowired
    private PublishDailyMaintenanceNoticesUseCase dailyMaintenanceNotices;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Autowired
    private DamageReportRepositoryPort damageReportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private ApplyScheduledTechnicalBlocksUseCase applyScheduledTechnicalBlocks;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
    }

    @AfterEach
    void resetSpy() {
        reset(applyScheduledTechnicalBlocks);
    }

    @Test
    @DisplayName("T016: tras la lista del día, la habitación apartada como Reserved no recibe su bloqueo de hoy y la"
            + " que quedó Available sí")
    void dailyList_reservedRoomKeepsReservation() {
        Room reserved = newRoom(RoomStatus.Available);
        TechnicalBlockReport reservedReport = scheduleCoveringToday(reserved);
        Room free = newRoom(RoomStatus.Available);
        TechnicalBlockReport freeReport = scheduleCoveringToday(free);

        assertThat(ingestDailyReservations.ingestDailyList(list(reservationArrivingToday(reserved))))
                .isEqualTo(DailyReservationIngestionOutcome.APPLIED);

        verify(applyScheduledTechnicalBlocks).runDaily(today);
        assertThat(roomStatus(reserved)).isEqualTo("Reserved");
        assertThat(reportStatus(reservedReport)).isEqualTo("Scheduled");
        assertThat(roomStatus(free)).isEqualTo("TechnicalBlock");
        assertThat(reportStatus(freeReport)).isEqualTo("Applied");
    }

    @Test
    @DisplayName("T016: si falla la aplicación de los bloqueos, la ingesta ya confirmada no se revierte")
    void runDailyFailure_doesNotRollBackIngestion() {
        Room room = newRoom(RoomStatus.Available);
        doThrow(new IllegalStateException("fallo forzado"))
                .when(applyScheduledTechnicalBlocks)
                .runDaily(any());
        DailyReservationListMessageDto message = list(reservationArrivingToday(room));

        assertThat(ingestDailyReservations.ingestDailyList(message))
                .isEqualTo(DailyReservationIngestionOutcome.APPLIED);

        verify(applyScheduledTechnicalBlocks).runDaily(today);
        assertThat(roomStatus(room)).isEqualTo("Reserved");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM daily_reservation_message_log WHERE message_id = ?",
                        Integer.class,
                        message.messageId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("T019: el aviso diario deja un SCHEDULED de hoy por la reparación (último daño) y el Applied vencido;"
            + " ninguno por el Applied vigente ni el Scheduled")
    void dailyNotice_onlyRepairsAndOverdueBlocks() {
        Room repair = newRoom(RoomStatus.DisabledForRepairs);
        damageReportRepository.save(DamageReport.report(
                repair.getId(),
                "mtorres",
                "Grieta en la pared.",
                OffsetDateTime.now(clock).minusDays(3)));
        DamageReport latestDamage = damageReportRepository.save(DamageReport.report(
                repair.getId(), "mtorres", "Ventana con el vidrio fisurado.", OffsetDateTime.now(clock)));
        Room overdue = newRoom(RoomStatus.TechnicalBlock);
        TechnicalBlockReport overdueReport = saveApplied(overdue, today.minusDays(1));
        Room current = newRoom(RoomStatus.TechnicalBlock);
        saveApplied(current, today);
        Room scheduled = newRoom(RoomStatus.Available);
        reportRepository.save(TechnicalBlockReport.schedule(
                scheduled.getId(), MEMBER, REASON, today.plusDays(1), today.plusDays(2), OffsetDateTime.now(clock)));

        dailyMaintenanceNotices.publishDaily(today);

        assertNotice(repair, latestDamage.getId());
        assertNotice(overdue, overdueReport.getId());
        assertThat(noticeRows(current)).isZero();
        assertThat(noticeRows(scheduled)).isZero();
    }

    /** Un solo aviso de la habitación: SCHEDULED, con ese maintenanceId e inicio = fin = hoy. */
    private void assertNotice(Room room, UUID maintenanceId) {
        assertThat(noticeRows(room)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT payload->>'maintenanceId' AS maintenance_id, payload->>'action' AS action, "
                                + "payload->>'startDate' AS start_date, payload->>'endDate' AS end_date "
                                + "FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                                + "AND payload->>'roomId' = ?",
                        room.getId().toString()))
                .isEqualTo(Map.of(
                        "maintenance_id", maintenanceId.toString(),
                        "action", "SCHEDULED",
                        "start_date", today.toString(),
                        "end_date", today.toString()));
    }

    private int noticeRows(Room room) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                        + "AND payload->>'roomId' = ?",
                Integer.class,
                room.getId().toString());
    }

    private TechnicalBlockReport scheduleCoveringToday(Room room) {
        return reportRepository.save(TechnicalBlockReport.schedule(
                room.getId(), MEMBER, REASON, today, today.plusDays(1), OffsetDateTime.now(clock)));
    }

    private TechnicalBlockReport saveApplied(Room room, LocalDate estimatedEnd) {
        TechnicalBlockReport report = TechnicalBlockReport.schedule(
                room.getId(),
                MEMBER,
                REASON,
                estimatedEnd.minusDays(2),
                estimatedEnd,
                OffsetDateTime.now(clock).minusDays(5));
        report.apply();
        return reportRepository.save(report);
    }

    private DailyReservationListMessageDto list(DailyReservation reservation) {
        return new DailyReservationListMessageDto(UUID.randomUUID().toString(), 1, today, List.of(reservation));
    }

    private DailyReservation reservationArrivingToday(Room room) {
        return new DailyReservation(
                "RES-" + ThreadLocalRandom.current().nextInt(100_000, 1_000_000),
                "Ana",
                "Gómez",
                "CC",
                "1020304050",
                "Colombia",
                "WEB",
                today,
                today.plusDays(2),
                2,
                OffsetDateTime.now(clock),
                List.of(new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 2)));
    }

    private String roomStatus(Room room) {
        return jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, room.getId());
    }

    private String reportStatus(TechnicalBlockReport report) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM technical_block_report WHERE id = ?", String.class, report.getId());
    }

    private Room newRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                "S" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("150000"),
                status,
                null,
                0));
    }
}
