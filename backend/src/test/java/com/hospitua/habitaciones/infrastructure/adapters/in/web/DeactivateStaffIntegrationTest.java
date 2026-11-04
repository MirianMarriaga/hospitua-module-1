package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.RegisterStaffAccountCommand;
import com.hospitua.habitaciones.application.dto.StartCleaningCommand;
import com.hospitua.habitaciones.application.ports.in.RegisterStaffAccountUseCase;
import com.hospitua.habitaciones.application.ports.in.StartCleaningUseCase;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffArea;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * {@code POST /api/staff/{username}/deactivation} sobre la aplicación completa y PostgreSQL real
 * (plan-gestionar-personal.md T029, T032, T035). La base es compartida: cada prueba crea sus cuentas y habitaciones, y
 * ninguna desactiva una cuenta de Administración (la concurrencia entre Administradores está en
 * {@code DeactivateLastAdministratorIntegrationTest}, con su propia base).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeactivateStaffIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String PASSWORD = "Limpieza2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterStaffAccountUseCase registerStaffAccountUseCase;

    @Autowired
    private StartCleaningUseCase startCleaningUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private ReparationTaskRepositoryPort reparationTaskRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("T035: responde 200 con active=false; la fila sigue en user_account con el responsable y la hora")
    void deactivate_keepsRow() throws Exception {
        String username = newAccount(StaffArea.RECEPCION);

        deactivate(username)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Jorge Ramírez"))
                .andExpect(jsonPath("$.area").value("RECEPCION"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT active, deactivated_by, deactivated_at FROM user_account WHERE username = ?", username);
        assertThat(row).containsEntry("active", false).containsEntry("deactivated_by", ADMIN);
        assertThat(row.get("deactivated_at")).isNotNull();
    }

    @Test
    @DisplayName("T032: la cuenta desactivada recibe 401 en el login, su X-User 400 y el historial conserva su nombre")
    void deactivated_cannotLogin_historyKeepsName() throws Exception {
        String member = newAccount(StaffArea.LIMPIEZA);
        UUID roomId = newRoom(RoomStatus.PendingCleaning);
        startCleaningUseCase.startCleaning(new StartCleaningCommand(roomId, member));
        login(member).andExpect(status().isOk());

        deactivate(member).andExpect(status().isOk());

        login(member)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        mockMvc.perform(get("/api/maintenance/panel").header("X-User", member))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        mockMvc.perform(get("/api/room-state-history").param("roomId", roomId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.status == 'InCleaning')].actorName")
                        .value("Jorge Ramírez"));
    }

    @Test
    @DisplayName("T029: con una tarea de limpieza abierta la cierra y la habitación vuelve a PendingCleaning")
    void deactivate_releasesCleaningTask() throws Exception {
        String member = newAccount(StaffArea.LIMPIEZA);
        UUID roomId = newRoom(RoomStatus.PendingCleaning);
        startCleaningUseCase.startCleaning(new StartCleaningCommand(roomId, member));
        assertThat(roomStatus(roomId)).isEqualTo("InCleaning");

        deactivate(member).andExpect(status().isOk());

        assertThat(roomStatus(roomId)).isEqualTo("PendingCleaning");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT outcome FROM cleaning_task WHERE cleaning_staff_member_id = ? AND end_date_time IS NULL",
                        member))
                .isEmpty();
    }

    @Test
    @DisplayName("T029: con una tarea de reparación abierta la libera sin cambiar la habitación")
    void deactivate_releasesRepairTask() throws Exception {
        String member = newAccount(StaffArea.MANTENIMIENTO);
        UUID roomId = newRoom(RoomStatus.DisabledForRepairs);
        reparationTaskRepository.save(ReparationTask.start(roomId, member, OffsetDateTime.now(clock)));

        deactivate(member).andExpect(status().isOk());

        assertThat(roomStatus(roomId)).isEqualTo("DisabledForRepairs");
        assertThat(jdbcTemplate.queryForList(
                        "SELECT outcome FROM reparation_task WHERE maintenance_staff_member_id = ?",
                        String.class,
                        member))
                .containsExactly("Released");
    }

    @Test
    @DisplayName(
            "T030: la propia cuenta, una ya desactivada y una inexistente responden 409, 409 y 404; sin X-User 400")
    void deactivate_rejected() throws Exception {
        deactivate(ADMIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_DEACTIVATE_SELF"))
                .andExpect(jsonPath("$.message").value("No puedes desactivar tu propia cuenta."));
        String username = newAccount(StaffArea.RECEPCION);
        deactivate(username).andExpect(status().isOk());
        deactivate(username)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_INACTIVE"))
                .andExpect(jsonPath("$.message").value("La cuenta ya está desactivada."));
        deactivate("nadie_" + UUID.randomUUID().toString().substring(0, 8))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_FOUND"));
        String other = newAccount(StaffArea.RECEPCION);
        mockMvc.perform(post("/api/staff/{username}/deactivation", other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT active FROM user_account WHERE username = ?", Boolean.class, other))
                .isTrue();
    }

    private String newAccount(StaffArea area) {
        String username = "des_" + UUID.randomUUID().toString().substring(0, 8);
        registerStaffAccountUseCase.register(
                new RegisterStaffAccountCommand(username, PASSWORD, "Jorge Ramírez", area), ADMIN);
        return username;
    }

    private UUID newRoom(RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "D" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        2,
                        RoomCategory.DOBLE,
                        2,
                        new BigDecimal("150000"),
                        status,
                        null,
                        0))
                .getId();
    }

    private String roomStatus(UUID roomId) {
        return jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, roomId);
    }

    private ResultActions deactivate(String username) throws Exception {
        return mockMvc.perform(
                post("/api/staff/{username}/deactivation", username).header("X-User", ADMIN));
    }

    private ResultActions login(String username) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"));
    }
}
