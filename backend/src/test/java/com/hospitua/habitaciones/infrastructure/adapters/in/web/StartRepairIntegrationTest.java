package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.StartRepairCommand;
import com.hospitua.habitaciones.application.ports.in.StartRepairUseCase;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.jayway.jsonpath.JsonPath;
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
 * {@code POST /api/maintenance/tasks} sobre la aplicación completa y PostgreSQL real
 * (plan-confirmar-reparacion-finalizada.md T009, T011). La base es compartida: cada prueba crea sus habitaciones y sus
 * cuentas de mantenimiento.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StartRepairIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StartRepairUseCase startRepairUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T011: responde 201 con la tarea; la habitación no cambia y el historial no se escribe")
    void start_createsTaskWithoutTransition() throws Exception {
        UUID roomId = newRoom(RoomStatus.DisabledForRepairs);
        String member = newMaintenanceMember();

        String body = start(member, roomId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        ReparationTask task = reparationTaskRepository
                .findById(UUID.fromString(JsonPath.read(body, "$.taskId")))
                .orElseThrow();
        assertThat(task.getRoomId()).isEqualTo(roomId);
        assertThat(task.getMaintenanceStaffMemberId()).isEqualTo(member);
        assertThat(task.isOpen()).isTrue();
        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.DisabledForRepairs);
        assertThat(countHistory(roomId)).isZero();
    }

    @Test
    @DisplayName("T011: desde TechnicalBlock también se inicia, y una fecha enviada por el cliente se ignora")
    void start_fromTechnicalBlock_ignoresClientDates() throws Exception {
        UUID roomId = newRoom(RoomStatus.TechnicalBlock);

        mockMvc.perform(post("/api/maintenance/tasks")
                        .header("X-User", newMaintenanceMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + roomId + "\",\"startDateTime\":\"2000-01-01T00:00:00-05:00\"}"))
                .andExpect(status().isCreated());

        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.TechnicalBlock);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT start_date_time > now() - interval '1 hour' FROM reparation_task WHERE room_id = ?",
                        Boolean.class,
                        roomId))
                .isTrue();
    }

    @Test
    @DisplayName("T011: los rechazos responden con el errorCode y el texto de la tabla de errores")
    void start_rejections() throws Exception {
        String member = newMaintenanceMember();
        UUID available = newRoom(RoomStatus.Available);
        start(member, available)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("Available"))
                .andExpect(jsonPath("$.message").value("La habitación está Disponible; no necesita reparación."));
        assertThat(countTasks(available)).isZero();

        UUID taken = newRoom(RoomStatus.DisabledForRepairs);
        start(newMaintenanceMember(), taken).andExpect(status().isCreated());
        start(member, taken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_TASK_EXISTS"))
                .andExpect(jsonPath("$.message").value("Otro compañero ya está reparando esta habitación."));

        start(member, newRoom(RoomStatus.TechnicalBlock)).andExpect(status().isCreated());
        start(member, newRoom(RoomStatus.DisabledForRepairs))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_TASK_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("Ya estás reparando otra habitación. Termínala o déjala antes de empezar otra."));
    }

    @Test
    @DisplayName(
            "Una habitación inexistente responde 404; sin roomId o con uno que no es UUID 400 VALIDATION_ERROR; sin"
                    + " X-User 400")
    void start_invalidRequests() throws Exception {
        String member = newMaintenanceMember();
        start(member, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
        Map<String, String> messages = Map.of(
                "{}", "Selecciona una habitación válida.",
                "{\"roomId\":\"no-es-un-uuid\"}", "Cuerpo de la petición inválido o mal formado.");
        for (Map.Entry<String, String> invalid : messages.entrySet()) {
            mockMvc.perform(post("/api/maintenance/tasks")
                            .header("X-User", member)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid.getKey()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.details.field").value("roomId"))
                    .andExpect(jsonPath("$.message").value(invalid.getValue()));
        }
        mockMvc.perform(post("/api/maintenance/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + newRoom(RoomStatus.DisabledForRepairs) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
    }

    @Test
    @DisplayName("T009: dos miembros inician la misma habitación a la vez; uno gana y el otro recibe ROOM_TASK_EXISTS")
    void start_concurrently_oneWins() throws Exception {
        UUID roomId = newRoom(RoomStatus.DisabledForRepairs);
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
                        return startRepairUseCase.startRepair(new StartRepairCommand(roomId, member));
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
            assertThat(outcomes)
                    .filteredOn(RoomTaskExistsException.class::isInstance)
                    .hasSize(1);
            assertThat(outcomes).filteredOn(RuntimeException.class::isInstance).hasSize(1);
            assertThat(countTasks(roomId)).isEqualTo(1);
            assertThat(roomRepository.findById(roomId).orElseThrow().getStatus())
                    .isEqualTo(RoomStatus.DisabledForRepairs);
        } finally {
            pool.shutdownNow();
        }
    }

    private ResultActions start(String member, UUID roomId) throws Exception {
        return mockMvc.perform(post("/api/maintenance/tasks")
                .header("X-User", member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":\"" + roomId + "\"}"));
    }

    private int countTasks(UUID roomId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reparation_task WHERE room_id = ?", Integer.class, roomId);
    }

    private int countHistory(UUID roomId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId);
    }

    private UUID newRoom(RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        2,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("150000"),
                        status,
                        null,
                        0))
                .getId();
    }

    /** Cuenta activa del área de mantenimiento, para que {@code X-User} la resuelva. */
    private String newMaintenanceMember() {
        String username = newMember();
        staffAccountRepository.save(StaffAccount.createNew(
                username,
                "$2a$10$hash",
                "Personal de mantenimiento",
                StaffArea.MANTENIMIENTO,
                OffsetDateTime.now(),
                "admin"));
        return username;
    }

    private static String newMember() {
        return "mant_" + UUID.randomUUID().toString().substring(0, 8);
    }
}
