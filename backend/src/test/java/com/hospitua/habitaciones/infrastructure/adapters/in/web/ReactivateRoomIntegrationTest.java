package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.ReactivateRoomCommand;
import com.hospitua.habitaciones.application.dto.ReactivationResultDto;
import com.hospitua.habitaciones.application.ports.in.ReactivateRoomUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
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

/**
 * {@code GET /api/rooms/{roomId}/decommission} y {@code POST /api/rooms/{roomId}/reactivation} sobre la aplicación
 * completa y PostgreSQL real (plan-marcar-habitacion-como-disponible.md T015, T020). La base es compartida: cada prueba
 * crea su habitación inactiva con su baja; las bajas de la semilla solo se leen.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReactivateRoomIntegrationTest {

    private static final String ADMIN = "admin";
    private static final UUID SEEDED_INACTIVE_ROOM = UUID.fromString("a0000000-0000-0000-0000-000000000104");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReactivateRoomUseCase reactivateRoomUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private RoomDecommissionRepositoryPort decommissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String roomNumber;
    private UUID roomId;
    private RoomDecommission decommission;

    @BeforeEach
    void setUp() {
        roomNumber = "R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
        roomId = newRoom(roomNumber, RoomStatus.Inactive);
        decommission = decommissionRepository.save(RoomDecommission.decommission(
                roomId,
                DecommissionReason.FLOOR_CLOSURE,
                "Cierre del piso 3.",
                ADMIN,
                OffsetDateTime.parse("2026-10-09T15:00:00-05:00")));
    }

    @Test
    @DisplayName("T014 por HTTP: la confirmación trae la habitación y su baja vigente")
    void currentDecommission_returnsRoomAndDecommission() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/decommission", roomId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.roomNumber").value(roomNumber))
                .andExpect(jsonPath("$.floor").value(3))
                .andExpect(jsonPath("$.categoryRoom").value("SUITE"))
                .andExpect(jsonPath("$.status").value("Inactive"))
                .andExpect(
                        jsonPath("$.decommissionId").value(decommission.getId().toString()))
                .andExpect(jsonPath("$.decommissionDateTime").value(endsWith("-05:00")))
                .andExpect(jsonPath("$.reasonCategory").value("FLOOR_CLOSURE"))
                .andExpect(jsonPath("$.reasonDetail").value("Cierre del piso 3."));
    }

    @Test
    @DisplayName("Las habitaciones que la semilla carga inactivas tienen su baja vigente")
    void currentDecommission_seededRoom() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/decommission", SEEDED_INACTIVE_ROOM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("104"))
                .andExpect(jsonPath("$.decommissionId").value("c0000000-0000-0000-0000-000000000104"))
                .andExpect(jsonPath("$.reasonCategory").value("ADMINISTRATIVE_DECISION"))
                .andExpect(jsonPath("$.reasonDetail").value("Baja preventiva administrativa inicial de prueba"));
    }

    @Test
    @DisplayName("Sin detalle en la baja, la confirmación omite reasonDetail")
    void currentDecommission_withoutDetail_omitsIt() throws Exception {
        UUID otherRoom =
                newRoom("R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000), RoomStatus.Inactive);
        decommissionRepository.save(RoomDecommission.decommission(
                otherRoom, DecommissionReason.PERMANENT_REMODELING, null, ADMIN, OffsetDateTime.now()));

        mockMvc.perform(get("/api/rooms/{roomId}/decommission", otherRoom))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCategory").value("PERMANENT_REMODELING"))
                .andExpect(jsonPath("$.reasonDetail").doesNotExist());
    }

    @Test
    @DisplayName("GET: 409 fuera de Inactive y 404 si la habitación no existe")
    void currentDecommission_errors() throws Exception {
        UUID available =
                newRoom("R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000), RoomStatus.Available);

        mockMvc.perform(get("/api/rooms/{roomId}/decommission", available))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación no está Inactiva (estado actual: Disponible); no es posible marcarla como"
                                + " disponible."))
                .andExpect(jsonPath("$.details.currentStatus").value("Available"));
        mockMvc.perform(get("/api/rooms/{roomId}/decommission", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("T011 y T020 por HTTP: reactiva, revierte la baja, abre el periodo MARK_AVAILABLE y aparece Available")
    void reactivate_thenRetry() throws Exception {
        reactivate(ADMIN, decommission.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.roomNumber").value(roomNumber))
                .andExpect(jsonPath("$.status").value("Available"))
                .andExpect(jsonPath("$.alreadyReactivated").value(false));

        assertThat(roomStatus()).isEqualTo("Available");
        Map<String, Object> reverted =
                jdbcTemplate.queryForMap("SELECT * FROM room_decommission WHERE id = ?", decommission.getId());
        assertThat(reverted).containsEntry("reactivated_by", ADMIN).containsEntry("decommissioned_by", ADMIN);
        Map<String, Object> period = jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", roomId);
        assertThat(period)
                .containsEntry("status", "Available")
                .containsEntry("previous_status", "Inactive")
                .containsEntry("source_flow", "MARK_AVAILABLE")
                .containsEntry("actor_id", ADMIN);
        assertThat(period.get("start_date_time")).isEqualTo(reverted.get("reactivation_date_time"));
        mockMvc.perform(get("/api/rooms/inventory")
                        .param("roomNumber", roomNumber)
                        .param("status", "Available"))
                .andExpect(jsonPath("$.items[0].roomId").value(roomId.toString()));

        reactivate(ADMIN, decommission.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Available"))
                .andExpect(jsonPath("$.alreadyReactivated").value(true));
        assertThat(countPeriods()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST: decommissionId ausente, mal formado o ajeno → 400; sin X-User → 400; 409 y 404")
    void reactivate_errors() throws Exception {
        String message = "No se pudo identificar la baja de la habitación. Vuelve a abrir la confirmación.";
        reactivate(ADMIN, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(message));
        reactivate(ADMIN, "no-es-un-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message));
        reactivate(ADMIN, UUID.randomUUID().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.details.field").value("decommissionId"));
        reactivate(null, decommission.getId().toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        assertThat(roomStatus()).isEqualTo("Inactive");

        UUID occupied =
                newRoom("R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000), RoomStatus.Occupied);
        mockMvc.perform(post("/api/rooms/{roomId}/reactivation", occupied)
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decommissionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("La habitación no está Inactiva (estado actual: Ocupada); no es posible marcarla como"
                                + " disponible."));
        mockMvc.perform(post("/api/rooms/{roomId}/reactivation", UUID.randomUUID())
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decommissionId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName(
            "T015: de dos reactivaciones simultáneas, una aplica la transición y la otra responde alreadyReactivated")
    void reactivate_concurrently_onePeriod() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReactivationResultDto>> results =
                    List.of(pool.submit(() -> reactivateAfter(go)), pool.submit(() -> reactivateAfter(go)));
            go.countDown();

            List<Boolean> already = List.of(
                    results.get(0).get().alreadyReactivated(),
                    results.get(1).get().alreadyReactivated());
            assertThat(already).containsExactlyInAnyOrder(false, true);
        } finally {
            pool.shutdownNow();
        }
        assertThat(roomStatus()).isEqualTo("Available");
        assertThat(countPeriods()).isEqualTo(1);
    }

    private ReactivationResultDto reactivateAfter(CountDownLatch go) throws InterruptedException {
        go.await();
        return reactivateRoomUseCase.reactivate(new ReactivateRoomCommand(roomId, decommission.getId(), ADMIN));
    }

    private ResultActions reactivate(String xUser, String decommissionId) throws Exception {
        var request = post("/api/rooms/{roomId}/reactivation", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(decommissionId == null ? "{}" : "{\"decommissionId\":\"" + decommissionId + "\"}");
        if (xUser != null) {
            request.header("X-User", xUser);
        }
        return mockMvc.perform(request);
    }

    private String roomStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, roomId);
    }

    private int countPeriods() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId);
    }

    private UUID newRoom(String number, RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(), number, 3, RoomCategory.SUITE, 4, new BigDecimal("320000"), status, null, 0))
                .getId();
    }
}
