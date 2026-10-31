package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.CompleteCleaningCommand;
import com.hospitua.habitaciones.application.ports.in.CompleteCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskOnDeactivationUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidDamageDescriptionException;
import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /api/cleaning/tasks/{taskId}/complete} y {@code …/release} sobre la aplicación completa y PostgreSQL real
 * (plan-confirmar-fin-de-limpieza.md T010–T012, T015, T018). Cada prueba parte de una tarea real
 * ({@code POST /api/cleaning/tasks}) sobre una habitación propia; la base es compartida.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompleteCleaningIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String DAMAGE = "Grifo del lavamanos con fuga constante.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompleteCleaningUseCase completeCleaningUseCase;

    @Autowired
    private ReleaseCleaningTaskUseCase releaseCleaningTaskUseCase;

    @Autowired
    private ReleaseCleaningTaskOnDeactivationUseCase releaseOnDeactivation;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    private Room room;
    private String member;
    private UUID taskId;

    @BeforeEach
    void startTask() throws Exception {
        room = roomRepository.save(new Room(
                UUID.randomUUID(),
                "C" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("150000"),
                RoomStatus.PendingCleaning,
                null,
                0));
        member = newCleaningMember();
        taskId = UUID.fromString(JsonPath.read(
                mockMvc.perform(post("/api/cleaning/tasks")
                                .header("X-User", member)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"roomId\":\"" + room.getId() + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.taskId"));
    }

    @Test
    @DisplayName("T012: sin daño ni llegada queda Available con un periodo CONFIRM_CLEANING_END del miembro")
    void complete_available() throws Exception {
        complete(member, null).andExpect(status().isNoContent());

        assertRoom(RoomStatus.Available);
        assertThat(periodsAfterCleaning())
                .containsExactly(tuple("Available", "InCleaning", "CONFIRM_CLEANING_END", member, null, true));
        assertTaskClosed(CleaningTaskOutcome.Completed, "Available");
    }

    @Test
    @DisplayName("T012: con llegada pendiente hoy pasa por Available y queda Reserved para esa reserva")
    void complete_reservedForPendingArrival() throws Exception {
        String ref = pendingArrivalToday();

        complete(member, "{}").andExpect(status().isNoContent());

        assertRoom(RoomStatus.Reserved);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getReservedByReservationRef())
                .isEqualTo(ref);
        assertThat(periodsAfterCleaning())
                .containsExactly(
                        tuple("Available", "InCleaning", "CONFIRM_CLEANING_END", member, null, false),
                        tuple("Reserved", "Available", "MARK_RESERVED", null, ref, true));
        assertTaskClosed(CleaningTaskOutcome.Completed, "Available");
    }

    @Test
    @DisplayName("T012: con daño queda DisabledForRepairs con su reporte y su aviso, aunque tenga llegada hoy")
    void complete_withDamage() throws Exception {
        pendingArrivalToday();

        complete(member, "{\"damageDescription\":\"" + DAMAGE + "\"}").andExpect(status().isNoContent());

        assertRoom(RoomStatus.DisabledForRepairs);
        assertThat(periodsAfterCleaning())
                .containsExactly(
                        tuple("Available", "InCleaning", "CONFIRM_CLEANING_END", member, null, false),
                        tuple("DisabledForRepairs", "Available", "CONFIRM_CLEANING_END", member, null, true));
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT author_id, damage_description FROM damage_report WHERE room_id = ?", room.getId()))
                .containsEntry("author_id", member)
                .containsEntry("damage_description", DAMAGE);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                                + "AND payload->>'roomId' = ? AND payload->>'action' = 'SCHEDULED'",
                        Integer.class,
                        room.getId().toString()))
                .isOne();
        assertTaskClosed(CleaningTaskOutcome.DamageReported, "Available");
    }

    @Test
    @DisplayName("T012: liberar deja PendingCleaning con un periodo RELEASE_CLEANING_TASK del miembro")
    void release_pendingCleaning() throws Exception {
        release(member).andExpect(status().isNoContent());

        assertRoom(RoomStatus.PendingCleaning);
        assertThat(periodsAfterCleaning())
                .containsExactly(tuple("PendingCleaning", "InCleaning", "RELEASE_CLEANING_TASK", member, null, true));
        assertTaskClosed(CleaningTaskOutcome.Released, "PendingCleaning");
    }

    @Test
    @DisplayName("T018: la tarea de otro miembro responde 403, una inexistente 404 y una cerrada 409")
    void endpoints_errors() throws Exception {
        String other = newCleaningMember();
        complete(other, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_OWNED"))
                .andExpect(jsonPath("$.message").value("Esta habitación la está limpiando otro compañero."));
        release(other).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cleaning/tasks/{taskId}/release", UUID.randomUUID())
                        .header("X-User", member))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TASK_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No encontramos esta limpieza. Vuelve al panel."));

        release(member).andExpect(status().isNoContent());

        complete(member, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TASK_ALREADY_CLOSED"))
                .andExpect(jsonPath("$.message").value("Esta tarea ya fue cerrada."));
        release(member).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("T018: un daño de más de 500 caracteres o sin X-User responde 400 sin cambios")
    void complete_invalidRequest() throws Exception {
        complete(member, "{\"damageDescription\":\"" + "a".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La descripción del daño admite máximo 500 caracteres."));
        mockMvc.perform(post("/api/cleaning/tasks/{taskId}/complete", taskId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));

        assertStillCleaning();
    }

    @Test
    @DisplayName(
            "T010: de una confirmación y una liberación simultáneas solo una gana; la otra recibe TASK_ALREADY_CLOSED")
    void completeAndRelease_concurrent() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> attempts = List.of(
                    () -> {
                        completeCleaningUseCase.completeCleaning(new CompleteCleaningCommand(taskId, member, null));
                        return "complete";
                    },
                    () -> {
                        releaseCleaningTaskUseCase.releaseTask(taskId, member);
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
            CleaningTask task = cleaningTaskRepository.findById(taskId).orElseThrow();
            assertThat(task.isOpen()).isFalse();
            assertRoom(
                    task.getOutcome() == CleaningTaskOutcome.Released
                            ? RoomStatus.PendingCleaning
                            : RoomStatus.Available);
            assertThat(periodsAfterCleaning()).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("T011: una falla en ReportDamageUseCase revierte todo: InCleaning y la tarea abierta")
    void complete_damageFailureRollsBack() {
        assertThatThrownBy(() -> completeCleaningUseCase.completeCleaning(
                        new CompleteCleaningCommand(taskId, member, "a".repeat(501))))
                .isInstanceOf(InvalidDamageDescriptionException.class);

        assertStillCleaning();
    }

    @Test
    @DisplayName("T015: releaseOpenTaskOf en la transacción de la desactivación libera con el Administrador como actor")
    void releaseOnDeactivation() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> releaseOnDeactivation.releaseOpenTaskOf(member, ADMIN));

        assertRoom(RoomStatus.PendingCleaning);
        assertThat(periodsAfterCleaning())
                .containsExactly(tuple("PendingCleaning", "InCleaning", "RELEASE_CLEANING_TASK", ADMIN, null, true));
        assertTaskClosed(CleaningTaskOutcome.Released, "PendingCleaning");
    }

    @Test
    @DisplayName("T015: si la desactivación falla después de liberar, la liberación también se revierte")
    void releaseOnDeactivation_rolledBack() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    releaseOnDeactivation.releaseOpenTaskOf(member, ADMIN);
                    throw new IllegalStateException("Falla de la desactivación");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertStillCleaning();
    }

    private ResultActions complete(String xUser, String body) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/api/cleaning/tasks/{taskId}/complete", taskId).header("X-User", xUser);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request);
    }

    private ResultActions release(String xUser) throws Exception {
        return mockMvc.perform(
                post("/api/cleaning/tasks/{taskId}/release", taskId).header("X-User", xUser));
    }

    /** Reserva de hoy en la copia local, sin estancia, con la habitación de la prueba. */
    private String pendingArrivalToday() {
        LocalDate today = LocalDate.now(clock);
        String ref = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        reservationRepository.save(new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123",
                "Colombia",
                "BOOKING",
                today,
                today.plusDays(1),
                1,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 1))));
        return ref;
    }

    /**
     * Periodos posteriores al {@code InCleaning} de la tarea: estado, estado previo, flujo, actor, referencia y si sigue
     * abierto. Los cerrados van primero.
     */
    private List<Tuple> periodsAfterCleaning() {
        return jdbcTemplate
                .queryForList(
                        "SELECT status, previous_status, source_flow, actor_id, reservation_ref, "
                                + "end_date_time IS NULL AS open FROM room_state_history "
                                + "WHERE room_id = ? AND status <> 'InCleaning' "
                                + "ORDER BY end_date_time IS NULL, start_date_time",
                        room.getId())
                .stream()
                .map(row -> tuple(
                        row.get("status"),
                        row.get("previous_status"),
                        row.get("source_flow"),
                        row.get("actor_id"),
                        row.get("reservation_ref"),
                        row.get("open")))
                .toList();
    }

    /** La tarea cerrada con su resultado y el fin igual al inicio del primer periodo tras la limpieza (FR-007). */
    private void assertTaskClosed(CleaningTaskOutcome outcome, String firstStatus) {
        Map<String, Object> task =
                jdbcTemplate.queryForMap("SELECT outcome, end_date_time FROM cleaning_task WHERE id = ?", taskId);
        assertThat(task.get("outcome")).isEqualTo(outcome.name());
        assertThat(task.get("end_date_time"))
                .isEqualTo(jdbcTemplate.queryForObject(
                        "SELECT start_date_time FROM room_state_history WHERE room_id = ? AND status = ? "
                                + "AND previous_status = 'InCleaning'",
                        Timestamp.class,
                        room.getId(),
                        firstStatus));
    }

    private void assertStillCleaning() {
        assertRoom(RoomStatus.InCleaning);
        assertThat(cleaningTaskRepository.findById(taskId).orElseThrow().isOpen())
                .isTrue();
        assertThat(periodsAfterCleaning()).isEmpty();
    }

    private void assertRoom(RoomStatus status) {
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(status);
    }

    private String newCleaningMember() {
        String username = "fin_" + UUID.randomUUID().toString().substring(0, 8);
        staffAccountRepository.save(StaffAccount.createNew(
                username, "$2a$10$hash", "Personal de limpieza", StaffArea.LIMPIEZA, OffsetDateTime.now(), "admin"));
        return username;
    }
}
