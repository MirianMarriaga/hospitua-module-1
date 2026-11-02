package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.EditRoomCommand;
import com.hospitua.habitaciones.application.ports.in.EditRoomUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ConcurrentRoomUpdateException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
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
 * {@code PATCH /api/rooms/{roomId}} sobre la aplicación completa y PostgreSQL real (plan-editar-habitacion.md T018,
 * T020). La base es compartida: cada prueba crea su habitación; la habitación inactiva de la semilla solo se usa como
 * número ocupado.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EditRoomIntegrationTest {

    private static final String ADMIN = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EditRoomUseCase editRoomUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID roomId;
    private String roomNumber;

    @BeforeEach
    void setUp() {
        roomNumber = uniqueNumber();
        roomId = newRoom(roomNumber, RoomStatus.Available);
    }

    @Test
    @DisplayName(
            "T004, T006 y T020: edita la tarifa, devuelve el cambio y la version nueva, audita y el inventario lo refleja")
    void edit_baseRate() throws Exception {
        edit(
                        ADMIN,
                        "{\"version\":0,\"roomNumber\":\"" + roomNumber
                                + "\",\"floor\":1,\"categoryRoom\":\"SENCILLA\","
                                + "\"maxCapacity\":2,\"baseRate\":195000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(true))
                .andExpect(jsonPath("$.changes.length()").value(1))
                .andExpect(jsonPath("$.changes[0].field").value("baseRate"))
                .andExpect(jsonPath("$.changes[0].previousValue").value(180000.0))
                .andExpect(jsonPath("$.changes[0].newValue").value(195000.0))
                .andExpect(jsonPath("$.room.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.room.baseRate").value(195000.0))
                .andExpect(jsonPath("$.room.status").value("Available"))
                .andExpect(jsonPath("$.room.version").value(1));

        Map<String, Object> audit = jdbcTemplate.queryForMap("SELECT * FROM room_audit_log WHERE room_id = ?", roomId);
        assertThat(audit).containsEntry("event_type", "ROOM_EDITED").containsEntry("actor_id", ADMIN);
        assertThat(audit.get("details").toString())
                .contains("\"field\": \"baseRate\"", "\"previousValue\": 180000", "\"newValue\": 195000");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId))
                .isZero();
        mockMvc.perform(get("/api/rooms/inventory").param("roomNumber", roomNumber))
                .andExpect(jsonPath("$.items[0].baseRate").value(195000.0))
                .andExpect(jsonPath("$.items[0].version").value(1));
    }

    @Test
    @DisplayName("T005: cambiar el número conserva el UUID; T007: sin cambios responde changed false sin auditar")
    void edit_numberThenNoChanges() throws Exception {
        String newNumber = uniqueNumber();
        edit(ADMIN, "{\"version\":0,\"roomNumber\":\" " + newNumber.toLowerCase() + " \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].field").value("roomNumber"))
                .andExpect(jsonPath("$.changes[0].previousValue").value(roomNumber))
                .andExpect(jsonPath("$.changes[0].newValue").value(newNumber))
                .andExpect(jsonPath("$.room.roomId").value(roomId.toString()));

        edit(ADMIN, "{\"version\":1,\"roomNumber\":\"" + newNumber + "\",\"floor\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changed").value(false))
                .andExpect(jsonPath("$.changes.length()").value(0))
                .andExpect(jsonPath("$.room.version").value(1));
        assertThat(countAudits()).isEqualTo(1);
    }

    @Test
    @DisplayName("T013, T017 y versión vieja: 409 con su código y sin cambios")
    void edit_conflicts() throws Exception {
        UUID occupied = newRoom(uniqueNumber(), RoomStatus.Occupied);
        mockMvc.perform(patch("/api/rooms/{roomId}", occupied)
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"floor\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación no está disponible para edición (estado actual: Ocupada)."))
                .andExpect(jsonPath("$.details.currentStatus").value("Occupied"));

        edit(ADMIN, "{\"version\":0,\"roomNumber\":\"104\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NUMBER_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("Ya existe una habitación con el número 104 (aunque esté inactiva)."));

        edit(ADMIN, "{\"version\":7,\"baseRate\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"))
                .andExpect(jsonPath("$.message").value(ConcurrentRoomUpdateException.MESSAGE));

        assertUnchanged();
    }

    @Test
    @DisplayName("T016: datos inválidos, sin version o sin X-User → 400 sin cambios; habitación inexistente → 404")
    void edit_invalidRequests() throws Exception {
        edit(ADMIN, "{\"version\":0,\"roomNumber\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("El número de habitación es obligatorio."))
                .andExpect(jsonPath("$.details.field").value("roomNumber"));
        edit(ADMIN, "{\"version\":0,\"floor\":1.5}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El piso debe ser un número entero mayor a 0."));
        edit(ADMIN, "{\"version\":0,\"categoryRoom\":\"PREMIUM\"}")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value("Selecciona un tipo válido: Sencilla, Doble, Suite o Boutique."));
        edit(ADMIN, "{\"version\":0,\"baseRate\":10.555}")
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value("La tarifa base debe ser mayor a 0 y tener máximo dos decimales."));
        edit(ADMIN, "{\"version\":0,\"maxCapacity\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La capacidad máxima debe ser un número entero mayor a 0."));
        edit(ADMIN, "{\"baseRate\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("version"));
        edit(null, "{\"version\":0,\"baseRate\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        mockMvc.perform(patch("/api/rooms/{roomId}", UUID.randomUUID())
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"baseRate\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró ninguna habitación con ese identificador."));

        assertUnchanged();
    }

    @Test
    @DisplayName(
            "T018: de dos ediciones simultáneas con la misma version, una se guarda y la otra recibe CONCURRENT_UPDATE")
    void edit_concurrently_secondIsRejected() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Throwable> failures = new ArrayList<>();
        try {
            List<Future<?>> edits =
                    List.of(pool.submit(() -> editAfter(go, "190000")), pool.submit(() -> editAfter(go, "200000")));
            go.countDown();
            for (Future<?> edit : edits) {
                try {
                    edit.get();
                } catch (ExecutionException ex) {
                    failures.add(ex.getCause());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(failures).singleElement().isInstanceOf(ConcurrentRoomUpdateException.class);
        assertThat(countAudits()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM room WHERE id = ?", Integer.class, roomId))
                .isEqualTo(1);
    }

    private Void editAfter(CountDownLatch go, String rate) throws InterruptedException {
        go.await();
        editRoomUseCase.edit(new EditRoomCommand(roomId, 0, null, null, null, null, new BigDecimal(rate), ADMIN));
        return null;
    }

    private ResultActions edit(String xUser, String body) throws Exception {
        var request = patch("/api/rooms/{roomId}", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (xUser != null) {
            request.header("X-User", xUser);
        }
        return mockMvc.perform(request);
    }

    private void assertUnchanged() {
        Map<String, Object> room = jdbcTemplate.queryForMap("SELECT * FROM room WHERE id = ?", roomId);
        assertThat(room).containsEntry("room_number", roomNumber).containsEntry("version", 0);
        assertThat(((BigDecimal) room.get("base_rate"))).isEqualByComparingTo("180000");
        assertThat(countAudits()).isZero();
    }

    private int countAudits() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_audit_log WHERE room_id = ?", Integer.class, roomId);
    }

    private UUID newRoom(String number, RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        number,
                        1,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("180000.00"),
                        status,
                        null,
                        0))
                .getId();
    }

    private static String uniqueNumber() {
        return "E" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
    }
}
