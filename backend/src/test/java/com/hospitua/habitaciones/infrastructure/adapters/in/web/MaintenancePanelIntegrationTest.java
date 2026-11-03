package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/maintenance/panel} sobre la aplicación completa y PostgreSQL real
 * (plan-consultar-panel-mantenimiento.md T006, T009). La base es compartida: el panel lista todas las habitaciones
 * salvo las {@code Inactive}, así que cada prueba revisa solo las que crea.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaintenancePanelIntegrationTest {

    private static final String REASON = "Cambio de tubería.";

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName(
            "T009: sin Inactive, en tres grupos, con overdue, hasOpenTask y maintenance (nulo si solo hay Expired);"
                    + " T006: la consulta no modifica room, reparation_task ni technical_block_report")
    void panel_listsRooms_readOnly() throws Exception {
        Room overdue = newRoom(RoomStatus.TechnicalBlock);
        saveApplied(overdue, today.minusDays(1));
        Room repair = newRoom(RoomStatus.DisabledForRepairs);
        reparationTaskRepository.save(
                ReparationTask.start(repair.getId(), newMaintenanceMember(), OffsetDateTime.now(clock)));
        Room occupied = newRoom(RoomStatus.Occupied);
        reportRepository.save(report(occupied, today.plusDays(5), today.plusDays(6)));
        Room expiredOnly = newRoom(RoomStatus.Available);
        TechnicalBlockReport expired = report(expiredOnly, today.minusDays(4), today.minusDays(2));
        expired.expire();
        reportRepository.save(expired);
        Room inactive = newRoom(RoomStatus.Inactive);
        List<UUID> ours =
                List.of(overdue.getId(), repair.getId(), occupied.getId(), expiredOnly.getId(), inactive.getId());
        List<Map<String, Object>> before = snapshot(ours);

        String body = mockMvc.perform(get("/api/maintenance/panel").header("X-User", newMaintenanceMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTask").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> roomIds = JsonPath.read(body, "$.rooms[*].roomId");
        assertThat(roomIds).doesNotContain(inactive.getId().toString());
        int overdueAt = roomIds.indexOf(overdue.getId().toString());
        int repairAt = roomIds.indexOf(repair.getId().toString());
        assertThat(overdueAt).isGreaterThanOrEqualTo(0).isLessThan(repairAt);
        assertThat(repairAt)
                .isLessThan(roomIds.indexOf(occupied.getId().toString()))
                .isLessThan(roomIds.indexOf(expiredOnly.getId().toString()));
        assertThat(field(body, overdue, "overdue")).containsExactly(true);
        assertThat(field(body, overdue, "maintenance.estimatedEndDate"))
                .containsExactly(today.minusDays(1).toString());
        assertThat(field(body, repair, "hasOpenTask")).containsExactly(true);
        assertThat(field(body, repair, "status")).containsExactly("DisabledForRepairs");
        assertThat(field(body, occupied, "maintenance.startDate"))
                .containsExactly(today.plusDays(5).toString());
        assertThat(field(body, occupied, "categoryRoom")).containsExactly("DOBLE");
        assertThat(field(body, expiredOnly, "maintenance")).isEmpty();
        assertThat(snapshot(ours)).isEqualTo(before);
    }

    @Test
    @DisplayName("T009: con tarea abierta responde activeTask con la fila de su habitación y rooms vacío")
    void panel_activeTask() throws Exception {
        String member = newMaintenanceMember();
        Room room = newRoom(RoomStatus.DisabledForRepairs);
        ReparationTask task =
                reparationTaskRepository.save(ReparationTask.start(room.getId(), member, OffsetDateTime.now(clock)));

        mockMvc.perform(get("/api/maintenance/panel").header("X-User", member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTask.taskId").value(task.getId().toString()))
                .andExpect(
                        jsonPath("$.activeTask.room.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.activeTask.room.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.activeTask.room.status").value("DisabledForRepairs"))
                .andExpect(jsonPath("$.activeTask.room.hasOpenTask").value(true))
                .andExpect(jsonPath("$.activeTask.room.overdue").value(false))
                .andExpect(jsonPath("$.rooms").isEmpty());
    }

    @Test
    @DisplayName("Sin X-User responde 400 CURRENT_USER_REQUIRED")
    void panel_withoutCurrentUser() throws Exception {
        mockMvc.perform(get("/api/maintenance/panel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
    }

    private static List<Object> field(String body, Room room, String path) {
        return JsonPath.read(body, "$.rooms[?(@.roomId == '" + room.getId() + "')]." + path);
    }

    /** Filas de las tres tablas para las habitaciones de la prueba, en un orden fijo. */
    private List<Map<String, Object>> snapshot(List<UUID> roomIds) {
        String ids = String.join(",", roomIds.stream().map(id -> "'" + id + "'").toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.addAll(jdbcTemplate.queryForList("SELECT * FROM room WHERE id IN (" + ids + ") ORDER BY id"));
        rows.addAll(
                jdbcTemplate.queryForList("SELECT * FROM reparation_task WHERE room_id IN (" + ids + ") ORDER BY id"));
        rows.addAll(jdbcTemplate.queryForList(
                "SELECT * FROM technical_block_report WHERE room_id IN (" + ids + ") ORDER BY id"));
        return rows;
    }

    private void saveApplied(Room room, LocalDate end) {
        TechnicalBlockReport report = report(room, end.minusDays(2), end);
        report.apply();
        reportRepository.save(report);
    }

    private TechnicalBlockReport report(Room room, LocalDate start, LocalDate end) {
        return TechnicalBlockReport.schedule(
                room.getId(),
                "lperez",
                REASON,
                start,
                end,
                OffsetDateTime.now(clock).minusDays(5));
    }

    private Room newRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                "M" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
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
                OffsetDateTime.now(),
                "admin"));
        return username;
    }
}
