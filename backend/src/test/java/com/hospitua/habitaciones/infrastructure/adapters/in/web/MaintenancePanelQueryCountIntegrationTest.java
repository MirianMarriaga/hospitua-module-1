package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El número de consultas de {@code GET /api/maintenance/panel} no crece con el número de habitaciones
 * (plan-consultar-panel-mantenimiento.md T013; FR-002, FR-006). Cuenta las sentencias SQL con las estadísticas de
 * Hibernate, en su propio contexto y su propia base. El worker del outbox, que consulta cada segundo, queda en pausa
 * para no sumar sentencias durante la medición.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.jpa.properties.hibernate.generate_statistics=true",
            "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN",
            "hospitua.outbox.interval=3600000"
        })
class MaintenancePanelQueryCountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Autowired
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("T013: con el triple de habitaciones, tareas e informes, el panel hace las mismas consultas")
    void panel_queryCountDoesNotGrowWithRooms() throws Exception {
        String member = newMaintenanceMember();
        addRoomsOfEveryKind(1);
        long few = statementsFor(member, 4);

        addRoomsOfEveryKind(2);
        long many = statementsFor(member, 12);

        assertThat(few)
                .as("las estadísticas de Hibernate cuentan las consultas")
                .isPositive();
        assertThat(many).isEqualTo(few);
    }

    /** Sentencias SQL de una consulta del panel, después de comprobar que lista al menos {@code minRooms} filas. */
    private long statementsFor(String member, int minRooms) throws Exception {
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        String body = mockMvc.perform(get("/api/maintenance/panel").header("X-User", member))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long statements = statistics.getPrepareStatementCount();
        List<String> rooms = JsonPath.read(body, "$.rooms[*].roomId");
        assertThat(rooms).hasSizeGreaterThanOrEqualTo(minRooms);
        return statements;
    }

    /**
     * Por cada vuelta, una habitación de cada caso que el panel resuelve con una consulta por lotes: con tarea abierta,
     * con informe {@code Applied}, con informe {@code Scheduled} y sin intervención.
     */
    private void addRoomsOfEveryKind(int times) {
        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < times; i++) {
            Room repair = newRoom(RoomStatus.DisabledForRepairs);
            reparationTaskRepository.save(
                    ReparationTask.start(repair.getId(), newMaintenanceMember(), OffsetDateTime.now(clock)));
            Room blocked = newRoom(RoomStatus.TechnicalBlock);
            TechnicalBlockReport applied = report(blocked, today.minusDays(1), today.plusDays(1));
            applied.apply();
            reportRepository.save(applied);
            Room scheduled = newRoom(RoomStatus.Available);
            reportRepository.save(report(scheduled, today.plusDays(5), today.plusDays(6)));
            newRoom(RoomStatus.Available);
        }
    }

    private TechnicalBlockReport report(Room room, LocalDate start, LocalDate end) {
        return TechnicalBlockReport.schedule(
                room.getId(),
                "lperez",
                "Cambio de tubería.",
                start,
                end,
                OffsetDateTime.now(clock).minusDays(5));
    }

    private Room newRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                "Q" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("150000"),
                status,
                null,
                0));
    }

    private String newMaintenanceMember() {
        String username = "mant_" + UUID.randomUUID().toString().substring(0, 8);
        staffAccountRepository.save(StaffAccount.createNew(
                username,
                "$2a$10$hash",
                "Personal de mantenimiento",
                StaffArea.MANTENIMIENTO,
                OffsetDateTime.now(clock),
                "admin"));
        return username;
    }
}
