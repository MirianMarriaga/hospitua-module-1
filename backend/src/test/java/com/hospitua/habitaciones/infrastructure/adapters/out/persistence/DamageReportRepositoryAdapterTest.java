package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code DamageReportRepositoryAdapter} contra PostgreSQL real (plan-marcar-habitacion-inhabilitada-por-reparaciones.md
 * T001, T002). La base es compartida: cada prueba crea sus habitaciones.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class DamageReportRepositoryAdapterTest {

    private static final OffsetDateTime REPORTED_AT = OffsetDateTime.parse("2026-10-08T11:20:00-05:00");

    @Autowired
    private DamageReportRepositoryPort damageReportRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Test
    @DisplayName("T002: guarda el reporte y findLatestByRoom devuelve el más reciente de la habitación")
    void saveAndFindLatest() {
        UUID roomId = newRoom();
        damageReportRepository.save(DamageReport.report(roomId, "mtorres", "Ventana fisurada.", REPORTED_AT));
        DamageReport latest = damageReportRepository.save(
                DamageReport.report(roomId, "jramirez", "Grifo con fuga.", REPORTED_AT.plusDays(3)));

        DamageReport read = damageReportRepository.findLatestByRoom(roomId).orElseThrow();

        assertThat(read).isEqualTo(latest);
        assertThat(read.getAuthorId()).isEqualTo("jramirez");
        assertThat(read.getDamageDescription()).isEqualTo("Grifo con fuga.");
        assertThat(read.getReportDateTime()).isAtSameInstantAs(REPORTED_AT.plusDays(3));
        assertThat(damageReportRepository.findLatestByRoom(newRoom())).isEmpty();
    }

    @Test
    @DisplayName(
            "T002: findLatestByRoomIds da el último reporte de cada habitación en una consulta y omite las sin reportes")
    void findLatestByRoomIds() {
        UUID first = newRoom();
        UUID second = newRoom();
        UUID withoutReports = newRoom();
        damageReportRepository.save(DamageReport.report(first, "mtorres", "Ventana fisurada.", REPORTED_AT));
        DamageReport firstLatest = damageReportRepository.save(
                DamageReport.report(first, "mtorres", "Puerta descolgada.", REPORTED_AT.plusDays(1)));
        DamageReport secondLatest =
                damageReportRepository.save(DamageReport.report(second, "jramirez", "Grifo con fuga.", REPORTED_AT));

        Map<UUID, DamageReport> latest =
                damageReportRepository.findLatestByRoomIds(List.of(first, second, withoutReports));

        assertThat(latest).containsOnly(Map.entry(first, firstLatest), Map.entry(second, secondLatest));
        assertThat(damageReportRepository.findLatestByRoomIds(List.of())).isEmpty();
    }

    private UUID newRoom() {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "D" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        2,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("150000"),
                        RoomStatus.DisabledForRepairs,
                        null,
                        0))
                .getId();
    }
}
