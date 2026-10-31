package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.StartCleaningCommand;
import com.hospitua.habitaciones.application.ports.in.StartCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.math.BigDecimal;
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

/**
 * {@code POST /api/cleaning/tasks} sobre la aplicación completa y PostgreSQL real (plan-marcar-habitacion-en-limpieza.md
 * T010, T011, T013). La base es compartida: cada prueba crea sus habitaciones y sus cuentas de limpieza.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StartCleaningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StartCleaningUseCase startCleaningUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T013: responde 201 con la tarea; la habitación queda InCleaning y el historial abre el periodo")
    void start_createsTaskAndTransitions() throws Exception {
        UUID roomId = newRoom(RoomStatus.PendingCleaning);
        String member = newCleaningMember();

        String taskId = start(member, roomId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.taskId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"taskId\":\"([^\"]+)\".*", "$1");

        CleaningTask task =
                cleaningTaskRepository.findById(UUID.fromString(taskId)).orElseThrow();
        assertThat(task.getRoomId()).isEqualTo(roomId);
        assertThat(task.getCleaningStaffMemberId()).isEqualTo(member);
        assertThat(task.isOpen()).isTrue();
        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.InCleaning);
        Map<String, Object> period = openPeriod(roomId);
        assertThat(period)
                .containsEntry("status", "InCleaning")
                .containsEntry("previous_status", "PendingCleaning")
                .containsEntry("source_flow", "START_CLEANING")
                .containsEntry("actor_id", member);
        assertThat(task.getStartDateTime())
                .isAtSameInstantAs(((java.sql.Timestamp) period.get("start_date_time"))
                        .toInstant()
                        .atOffset(task.getStartDateTime().getOffset()));
    }

    @Test
    @DisplayName("T006: también se inicia desde Available, y una fecha enviada por el cliente se ignora")
    void start_fromAvailable_ignoresClientDates() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);

        mockMvc.perform(post("/api/cleaning/tasks")
                        .header("X-User", newCleaningMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + roomId + "\",\"startDateTime\":\"2000-01-01T00:00:00-05:00\"}"))
                .andExpect(status().isCreated());

        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.InCleaning);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT start_date_time > now() - interval '1 hour' FROM cleaning_task WHERE room_id = ?",
                        Boolean.class,
                        roomId))
                .isTrue();
    }

    @Test
    @DisplayName("T007: una habitación Occupied responde 409 ROOM_INVALID_STATE con su estado y no crea la tarea")
    void start_occupiedRoom() throws Exception {
        UUID roomId = newRoom(RoomStatus.Occupied);

        start(newCleaningMember(), roomId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("Occupied"))
                .andExpect(jsonPath("$.message").value("La habitación está Ocupada; ahora no se puede limpiar."));

        assertThat(countTasks(roomId)).isZero();
    }

    @Test
    @DisplayName("T008: un miembro con una tarea abierta responde 409 ACTIVE_TASK_EXISTS y la habitación no cambia")
    void start_memberWithOpenTask() throws Exception {
        String member = newCleaningMember();
        start(member, newRoom(RoomStatus.PendingCleaning)).andExpect(status().isCreated());
        UUID second = newRoom(RoomStatus.PendingCleaning);

        start(member, second)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_TASK_EXISTS"));

        assertThat(roomRepository.findById(second).orElseThrow().getStatus()).isEqualTo(RoomStatus.PendingCleaning);
    }

    @Test
    @DisplayName("Una habitación inexistente responde 404; sin roomId 400 VALIDATION_ERROR; sin X-User 400")
    void start_invalidRequests() throws Exception {
        String member = newCleaningMember();
        start(member, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
        mockMvc.perform(post("/api/cleaning/tasks")
                        .header("X-User", member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("roomId"));
        mockMvc.perform(post("/api/cleaning/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + newRoom(RoomStatus.PendingCleaning) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
    }

    @Test
    @DisplayName("T010: dos miembros inician la misma habitación a la vez; uno gana y el otro ve InCleaning")
    void start_concurrently_oneWins() throws Exception {
        UUID roomId = newRoom(RoomStatus.PendingCleaning);
        List<String> members = List.of(newMember(), newMember());
        CountDownLatch ready = new CountDownLatch(members.size());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(members.size());
        try {
            List<Future<Object>> results = new ArrayList<>();
            for (String member : members) {
                Callable<Object> attempt = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return startCleaningUseCase.startCleaning(new StartCleaningCommand(roomId, member));
                    } catch (RuntimeException rejected) {
                        return rejected;
                    }
                };
                results.add(pool.submit(attempt));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            List<InvalidRoomTransitionException> rejections = outcomes.stream()
                    .filter(InvalidRoomTransitionException.class::isInstance)
                    .map(InvalidRoomTransitionException.class::cast)
                    .toList();
            assertThat(rejections).hasSize(1);
            assertThat(rejections.getFirst().getCurrentStatus()).isEqualTo(RoomStatus.InCleaning);
            assertThat(countTasks(roomId)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("T011: si falla el guardado de la tarea, la transición y el periodo del historial se revierten")
    void start_failureRollsBackTransitionAndHistory() {
        UUID roomId = newRoom(RoomStatus.PendingCleaning);
        // Dato inconsistente a propósito: una tarea abierta sobre una habitación PendingCleaning hace fallar el
        // índice único por habitación al final de la transacción, después de la transición y del historial.
        // StartCleaningServiceTest falla si el servicio empieza a revisar esa tarea antes de la transición
        cleaningTaskRepository.save(CleaningTask.start(roomId, newMember(), OffsetDateTime.now()));

        assertThatThrownBy(() -> startCleaningUseCase.startCleaning(new StartCleaningCommand(roomId, newMember())))
                .isInstanceOf(RoomTaskExistsException.class);

        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.PendingCleaning);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId))
                .isZero();
        assertThat(countTasks(roomId)).isEqualTo(1);
    }

    private ResultActions start(String member, UUID roomId) throws Exception {
        return mockMvc.perform(post("/api/cleaning/tasks")
                .header("X-User", member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\"}"));
    }

    private Map<String, Object> openPeriod(UUID roomId) {
        return jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", roomId);
    }

    private int countTasks(UUID roomId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cleaning_task WHERE room_id = ?", Integer.class, roomId);
    }

    private UUID newRoom(RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "M" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        2,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("150000"),
                        status,
                        null,
                        0))
                .getId();
    }

    /** Cuenta activa del área de limpieza, para que {@code X-User} la resuelva. */
    private String newCleaningMember() {
        String username = newMember();
        staffAccountRepository.save(StaffAccount.createNew(
                username, "$2a$10$hash", "Personal de limpieza", StaffArea.LIMPIEZA, OffsetDateTime.now(), "admin"));
        return username;
    }

    private static String newMember() {
        return "limp_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
