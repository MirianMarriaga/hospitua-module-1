package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.in.CompleteRepairUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkPendingCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseRepairTaskUseCase;
import com.hospitua.habitaciones.application.ports.out.DamageReportRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.ReparationTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /api/maintenance/tasks/{taskId}/complete} y {@code …/release}, y la liberación por desactivación, sobre la
 * aplicación completa y PostgreSQL real (plan-confirmar-reparacion-finalizada.md T017, T018, T021, T024). Cada prueba
 * parte de una tarea real ({@code POST /api/maintenance/tasks}) sobre una habitación propia. El espía de
 * {@link MarkPendingCleaningUseCase} (T018) le da a esta clase su propio contexto y su propia base.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompleteRepairIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompleteRepairUseCase completeRepairUseCase;

    @Autowired
    private ReleaseRepairTaskUseCase releaseRepairTaskUseCase;

    @Autowired
    private ReleaseRepairTaskOnDeactivationUseCase releaseOnDeactivation;

    @MockitoSpyBean
    private MarkPendingCleaningUseCase markPendingCleaningUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Autowired
    private TechnicalBlockReportRepositoryPort technicalBlockReportRepository;

    @Autowired
    private DamageReportRepositoryPort damageReportRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName(
            "T024: desde DisabledForRepairs queda PendingCleaning con un periodo CONFIRM_REPAIR_END del miembro, la"
                    + " tarea Completed y el aviso CANCELLED del último reporte de daño")
    void complete_disabledForRepairs() throws Exception {
        Room room = newRoom(RoomStatus.DisabledForRepairs);
        DamageReport damage = damageReportRepository.save(
                DamageReport.report(room.getId(), "jramirez", "Grifo con fuga.", OffsetDateTime.now(clock)));
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);

        complete(member, taskId).andExpect(status().isNoContent());

        assertRoom(room, RoomStatus.PendingCleaning);
        assertThat(periods(room))
                .containsExactly(tuple("PendingCleaning", "DisabledForRepairs", "CONFIRM_REPAIR_END", member, true));
        assertTaskClosed(taskId, ReparationTaskOutcome.Completed);
        assertTaskEndIsPendingCleaningStart(taskId, room);
        String today = LocalDate.now(clock).toString();
        assertThat(notices(room))
                .containsExactly(Map.of(
                        "action",
                        "CANCELLED",
                        "maintenanceId",
                        damage.getId().toString(),
                        "startDate",
                        today,
                        "endDate",
                        today));
    }

    @Test
    @DisplayName("T024: desde TechnicalBlock el informe queda Completed y, cerrado después de la fecha estimada, se"
            + " publica UPDATED con el rango original")
    void complete_technicalBlock() throws Exception {
        Room room = newRoom(RoomStatus.TechnicalBlock);
        LocalDate today = LocalDate.now(clock);
        TechnicalBlockReport report = appliedReport(room, today.minusDays(3), today.minusDays(1));
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);

        complete(member, taskId).andExpect(status().isNoContent());

        assertRoom(room, RoomStatus.PendingCleaning);
        assertThat(reportStatus(report)).isEqualTo("Completed");
        assertTaskClosed(taskId, ReparationTaskOutcome.Completed);
        assertThat(notices(room))
                .containsExactly(Map.of(
                        "action", "UPDATED",
                        "maintenanceId", report.getId().toString(),
                        "startDate", today.minusDays(3).toString(),
                        "endDate", today.toString(),
                        "previousStartDate", today.minusDays(3).toString(),
                        "previousEndDate", today.minusDays(1).toString()));
    }

    @Test
    @DisplayName("T024: liberar cierra Released sin cambiar la habitación, su informe ni el historial, y sin avisos")
    void release_keepsRoom() throws Exception {
        Room room = newRoom(RoomStatus.TechnicalBlock);
        LocalDate today = LocalDate.now(clock);
        TechnicalBlockReport report = appliedReport(room, today.minusDays(1), today.plusDays(1));
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);

        release(member, taskId).andExpect(status().isNoContent());

        assertRoom(room, RoomStatus.TechnicalBlock);
        assertThat(reportStatus(report)).isEqualTo("Applied");
        assertThat(periods(room)).isEmpty();
        assertThat(notices(room)).isEmpty();
        assertTaskClosed(taskId, ReparationTaskOutcome.Released);
    }

    @Test
    @DisplayName("T024: la tarea de otro miembro responde 403, una inexistente 404, una cerrada 409 y sin X-User 400")
    void endpoints_errors() throws Exception {
        Room room = newRoom(RoomStatus.DisabledForRepairs);
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);
        String other = newMaintenanceMember();

        complete(other, taskId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_OWNED"))
                .andExpect(jsonPath("$.message").value("Esta reparación la está haciendo otro compañero."));
        release(other, taskId).andExpect(status().isForbidden());
        complete(member, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No encontramos esta reparación. Vuelve al panel."));
        mockMvc.perform(post("/api/maintenance/tasks/{taskId}/complete", taskId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        assertRoom(room, RoomStatus.DisabledForRepairs);

        release(member, taskId).andExpect(status().isNoContent());

        complete(member, taskId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TASK_ALREADY_CLOSED"))
                .andExpect(jsonPath("$.message").value("Esta tarea ya fue cerrada."));
        release(member, taskId).andExpect(status().isConflict());
        assertRoom(room, RoomStatus.DisabledForRepairs);
    }

    @Test
    @DisplayName("T017: de una confirmación y una liberación simultáneas solo una gana; la otra recibe"
            + " TASK_ALREADY_CLOSED")
    void completeAndRelease_concurrent() throws Exception {
        Room room = newRoom(RoomStatus.DisabledForRepairs);
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> attempts = List.of(
                    () -> {
                        completeRepairUseCase.completeRepair(taskId, member);
                        return "complete";
                    },
                    () -> {
                        releaseRepairTaskUseCase.releaseTask(taskId, member);
                        return "release";
                    });
            List<Future<Object>> results = new ArrayList<>();
            for (Callable<Object> attempt : attempts) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return attempt.call();
                    } catch (TaskAlreadyClosedException ex) {
                        return ex;
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes)
                    .filteredOn(TaskAlreadyClosedException.class::isInstance)
                    .hasSize(1);
            ReparationTask task = reparationTaskRepository.findById(taskId).orElseThrow();
            assertThat(task.isOpen()).isFalse();
            boolean completed = task.getOutcome() == ReparationTaskOutcome.Completed;
            assertRoom(room, completed ? RoomStatus.PendingCleaning : RoomStatus.DisabledForRepairs);
            assertThat(periods(room)).hasSize(completed ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("T018: una falla en MarkPendingCleaningUseCase revierte el cierre de la tarea y el informe, sin aviso")
    void complete_failureRollsBack() throws Exception {
        Room room = newRoom(RoomStatus.TechnicalBlock);
        LocalDate today = LocalDate.now(clock);
        TechnicalBlockReport report = appliedReport(room, today.minusDays(3), today.minusDays(1));
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);
        // El espía queda detrás del proxy transaccional (MANDATORY): se programa sobre el objeto espiado, porque
        // llamar al método a través del proxy fuera de una transacción falla antes de llegar a Mockito
        MarkPendingCleaningUseCase spied = AopTestUtils.getUltimateTargetObject(markPendingCleaningUseCase);
        doThrow(new IllegalStateException("Falla forzada de la transición"))
                .when(spied)
                .markPendingCleaning(any());

        assertThatThrownBy(() -> completeRepairUseCase.completeRepair(taskId, member))
                .isInstanceOf(IllegalStateException.class);

        assertRoom(room, RoomStatus.TechnicalBlock);
        assertThat(reportStatus(report)).isEqualTo("Applied");
        assertThat(reparationTaskRepository.findById(taskId).orElseThrow().isOpen())
                .isTrue();
        assertThat(periods(room)).isEmpty();
        assertThat(notices(room)).isEmpty();
    }

    @Test
    @DisplayName("T021: releaseOpenTaskOf en la transacción de la desactivación cierra Released sin tocar la habitación"
            + " ni su informe")
    void releaseOnDeactivation() throws Exception {
        Room room = newRoom(RoomStatus.TechnicalBlock);
        LocalDate today = LocalDate.now(clock);
        TechnicalBlockReport report = appliedReport(room, today.minusDays(1), today.plusDays(1));
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> releaseOnDeactivation.releaseOpenTaskOf(member));

        assertTaskClosed(taskId, ReparationTaskOutcome.Released);
        assertRoom(room, RoomStatus.TechnicalBlock);
        assertThat(reportStatus(report)).isEqualTo("Applied");
        assertThat(periods(room)).isEmpty();
    }

    @Test
    @DisplayName("T021: si la desactivación falla después de liberar, la liberación también se revierte; fuera de una"
            + " transacción se rechaza")
    void releaseOnDeactivation_rolledBack() throws Exception {
        Room room = newRoom(RoomStatus.DisabledForRepairs);
        String member = newMaintenanceMember();
        UUID taskId = startTask(member, room);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    releaseOnDeactivation.releaseOpenTaskOf(member);
                    throw new IllegalStateException("Falla de la desactivación");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> releaseOnDeactivation.releaseOpenTaskOf(member))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(reparationTaskRepository.findById(taskId).orElseThrow().isOpen())
                .isTrue();
    }

    private UUID startTask(String member, Room room) throws Exception {
        return UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/maintenance/tasks")
                                .header("X-User", member)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roomId\":\"" + room.getId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.taskId"));
    }

    private ResultActions complete(String xUser, UUID taskId) throws Exception {
        return mockMvc.perform(
                post("/api/maintenance/tasks/{taskId}/complete", taskId).header("X-User", xUser));
    }

    private ResultActions release(String xUser, UUID taskId) throws Exception {
        return mockMvc.perform(
                post("/api/maintenance/tasks/{taskId}/release", taskId).header("X-User", xUser));
    }

    private TechnicalBlockReport appliedReport(Room room, LocalDate start, LocalDate estimatedEnd) {
        TechnicalBlockReport report = TechnicalBlockReport.schedule(
                room.getId(),
                "mtorres",
                "Cambio preventivo del aire acondicionado.",
                start,
                estimatedEnd,
                OffsetDateTime.now(clock).minusDays(5));
        report.apply();
        return technicalBlockReportRepository.save(report);
    }

    private String reportStatus(TechnicalBlockReport report) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM technical_block_report WHERE id = ?", String.class, report.getId());
    }

    /** Periodos del historial de la habitación: estado, estado previo, flujo, actor y si sigue abierto. */
    private List<Tuple> periods(Room room) {
        return jdbcTemplate
                .queryForList(
                        "SELECT status, previous_status, source_flow, actor_id, end_date_time IS NULL AS open "
                                + "FROM room_state_history WHERE room_id = ? ORDER BY start_date_time",
                        room.getId())
                .stream()
                .map(row -> tuple(
                        row.get("status"),
                        row.get("previous_status"),
                        row.get("source_flow"),
                        row.get("actor_id"),
                        row.get("open")))
                .toList();
    }

    /** Avisos de mantenimiento de la habitación en el outbox, solo con los campos que trae cada uno. */
    private List<Map<String, String>> notices(Room room) {
        return jdbcTemplate
                .queryForList(
                        "SELECT payload->>'action' AS action, payload->>'maintenanceId' AS maintenance_id, "
                                + "payload->>'startDate' AS start_date, payload->>'endDate' AS end_date, "
                                + "payload->>'previousStartDate' AS previous_start_date, "
                                + "payload->>'previousEndDate' AS previous_end_date "
                                + "FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                                + "AND payload->>'roomId' = ?",
                        room.getId().toString())
                .stream()
                .map(row -> {
                    Map<String, String> notice = new LinkedHashMap<>();
                    putIfPresent(notice, "action", row.get("action"));
                    putIfPresent(notice, "maintenanceId", row.get("maintenance_id"));
                    putIfPresent(notice, "startDate", row.get("start_date"));
                    putIfPresent(notice, "endDate", row.get("end_date"));
                    putIfPresent(notice, "previousStartDate", row.get("previous_start_date"));
                    putIfPresent(notice, "previousEndDate", row.get("previous_end_date"));
                    return notice;
                })
                .toList();
    }

    private static void putIfPresent(Map<String, String> notice, String key, Object value) {
        if (value != null) {
            notice.put(key, value.toString());
        }
    }

    private void assertTaskClosed(UUID taskId, ReparationTaskOutcome outcome) {
        ReparationTask task = reparationTaskRepository.findById(taskId).orElseThrow();
        assertThat(task.isOpen()).isFalse();
        assertThat(task.getOutcome()).isEqualTo(outcome);
    }

    /** El fin de la tarea es el inicio del periodo PendingCleaning (FR-009). */
    private void assertTaskEndIsPendingCleaningStart(UUID taskId, Room room) {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT end_date_time FROM reparation_task WHERE id = ?", Timestamp.class, taskId))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT start_date_time FROM room_state_history WHERE room_id = ? AND status = 'PendingCleaning'",
                        Timestamp.class,
                        room.getId()));
    }

    private void assertRoom(Room room, RoomStatus status) {
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(status);
    }

    private Room newRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                "F" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("150000"),
                status,
                null,
                0));
    }

    /** Cuenta activa del área de mantenimiento, para que {@code X-User} la resuelva. */
    private String newMaintenanceMember() {
        String username = "rep_" + UUID.randomUUID().toString().substring(0, 8);
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
