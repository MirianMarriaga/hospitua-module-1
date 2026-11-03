package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code TechnicalBlockReportRepositoryAdapter} contra PostgreSQL real (plan-programar-bloqueo-tecnico-para-habitacion.md
 * T003). La base es compartida: cada prueba usa habitaciones propias y fechas de un año lejano y distinto, así las
 * consultas por fecha no ven informes de otras pruebas. Ojo: {@code expireScheduledEndedBefore(base)} caduca todos los
 * {@code Scheduled} de la base compartida que terminan antes de {@code base}, no solo los de esta clase; una prueba que
 * deje informes preparados para otra no debe depender de que sigan en {@code Scheduled}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TechnicalBlockReportRepositoryAdapterTest {

    private static final OffsetDateTime REPORTED_AT = OffsetDateTime.parse("2026-10-08T15:00:00-05:00");

    @Autowired
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    private LocalDate base;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        base = LocalDate.of(ThreadLocalRandom.current().nextInt(2400, 9000), 3, 1);
        roomId = newRoom();
    }

    @Test
    @DisplayName("save inserta el informe y guarda su cambio de estado (ida y vuelta)")
    void save_roundTrip() {
        TechnicalBlockReport report = reportRepository.save(scheduled(roomId, 0, 2));
        report.apply();
        reportRepository.save(report);

        TechnicalBlockReport applied =
                reportRepository.findAppliedByRoomIds(List.of(roomId)).get(roomId);
        assertThat(applied.getId()).isEqualTo(report.getId());
        assertThat(applied.getStatus()).isEqualTo(TechnicalBlockStatus.Applied);
        assertThat(applied.getReason()).isEqualTo("Cambio de tubería.");
        assertThat(applied.getMaintenanceStaffMemberId()).isEqualTo("mtorres");
        assertThat(applied.getStartDate()).isEqualTo(base);
        assertThat(applied.getEstimatedEndDate()).isEqualTo(base.plusDays(2));
        assertThat(applied.getReportDateTime()).isAtSameInstantAs(REPORTED_AT);
    }

    @Test
    @DisplayName("findActiveOverlapping: Scheduled y Applied que se cruzan (días inclusivos), ordenados por inicio")
    void findActiveOverlapping_inclusiveAndOrdered() {
        TechnicalBlockReport later = reportRepository.save(scheduled(roomId, 10, 12));
        TechnicalBlockReport first = scheduled(roomId, 0, 2);
        first.apply();
        reportRepository.save(first);
        TechnicalBlockReport expired = scheduled(roomId, 4, 6);
        expired.expire();
        reportRepository.save(expired);
        reportRepository.save(scheduled(newRoom(), 0, 20));

        assertThat(reportRepository.findActiveOverlapping(roomId, base.plusDays(2), base.plusDays(10)))
                .extracting(TechnicalBlockReport::getId)
                .containsExactly(first.getId(), later.getId());
        assertThat(reportRepository.findActiveOverlapping(roomId, base.plusDays(3), base.plusDays(9)))
                .isEmpty();
    }

    @Test
    @DisplayName("findEarliestScheduledByRoomIds: el Scheduled de inicio más temprano de cada habitación")
    void findEarliestScheduledByRoomIds() {
        UUID other = newRoom();
        reportRepository.save(scheduled(roomId, 30, 31));
        TechnicalBlockReport earliest = reportRepository.save(scheduled(roomId, 5, 6));
        TechnicalBlockReport otherOnly = reportRepository.save(scheduled(other, 1, 1));

        Map<UUID, TechnicalBlockReport> byRoom =
                reportRepository.findEarliestScheduledByRoomIds(List.of(roomId, other, newRoom()));

        assertThat(byRoom).containsOnlyKeys(roomId, other);
        assertThat(byRoom.get(roomId).getId()).isEqualTo(earliest.getId());
        assertThat(byRoom.get(other).getId()).isEqualTo(otherOnly.getId());
        assertThat(reportRepository.findEarliestScheduledByRoomIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("Un segundo Applied en la misma habitación lo rechaza el índice único parcial")
    void secondApplied_rejectedByIndex() {
        TechnicalBlockReport first = scheduled(roomId, 0, 1);
        first.apply();
        reportRepository.save(first);
        TechnicalBlockReport second = scheduled(roomId, 2, 3);
        second.apply();

        assertThatThrownBy(() -> reportRepository.save(second)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Trabajo diario: habitaciones con Scheduled que cubre hoy, y caducidad de los que ya terminaron")
    void dailyQueries() {
        UUID coveredRoom = newRoom();
        reportRepository.save(scheduled(coveredRoom, 0, 3));
        reportRepository.save(scheduled(roomId, -5, -1));
        TechnicalBlockReport appliedEnded = scheduled(newRoom(), -5, -1);
        appliedEnded.apply();
        reportRepository.save(appliedEnded);

        assertThat(reportRepository.findRoomIdsWithScheduledCovering(base))
                .contains(coveredRoom)
                .doesNotContain(roomId, appliedEnded.getRoomId());

        assertThat(reportRepository.expireScheduledEndedBefore(base)).isGreaterThanOrEqualTo(1);
        assertThat(reportRepository.findActiveOverlapping(roomId, base.minusDays(5), base))
                .isEmpty();
        assertThat(reportRepository.findAppliedByRoomIds(List.of(appliedEnded.getRoomId())))
                .containsKey(appliedEnded.getRoomId());
        assertThat(reportRepository.findRoomIdsWithScheduledCovering(base)).contains(coveredRoom);
    }

    private TechnicalBlockReport scheduled(UUID room, int fromDays, int toDays) {
        return TechnicalBlockReport.schedule(
                room, "mtorres", "Cambio de tubería.", base.plusDays(fromDays), base.plusDays(toDays), REPORTED_AT);
    }

    private UUID newRoom() {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "T" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        4,
                        RoomCategory.DOBLE,
                        2,
                        new BigDecimal("150000.00"),
                        RoomStatus.Available,
                        null,
                        0))
                .getId();
    }
}
