package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.in.TransitionRoomStateUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomDecommissionRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /api/rooms/{roomId}/decommission} sobre la aplicación completa, PostgreSQL real y Módulo 2 en WireMock
 * (plan-dar-de-baja-habitacion.md T011, T012, T018, T019). WireMock escucha en el puerto de {@code MODULE2_BASE_URL}
 * del perfil {@code test}. El espía del puerto de bajas (T012) le da a esta clase su propio contexto.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DecommissionRoomIntegrationTest {

    private static final int MODULE2_PORT = 8082;
    private static final String ADMIN = "admin";
    private static final String NO_RESERVATIONS = "{\"items\": []}";

    private static WireMockServer module2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private TransitionRoomStateUseCase transitionRoomStateUseCase;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private RoomDecommissionRepositoryPort decommissionRepository;

    private UUID roomId;
    private String roomNumber;

    @BeforeAll
    static void startModule2() {
        module2 = new WireMockServer(WireMockConfiguration.wireMockConfig().port(MODULE2_PORT));
        module2.start();
    }

    @AfterAll
    static void stopModule2() {
        module2.stop();
    }

    @BeforeEach
    void setUp() {
        module2.resetAll();
        roomNumber = "B" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
        roomId = newRoom(roomNumber, RoomStatus.Available);
    }

    @Test
    @DisplayName("T019 y T018: sin reservas en hoy..hoy+365, queda Inactive con su baja y su periodo DECOMMISSION_ROOM")
    void decommission_withoutReservations() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);

        decommission(ADMIN, "{\"reasonCategory\":\"FLOOR_CLOSURE\",\"reasonDetail\":\"  Cierre del piso 3.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId.toString()))
                .andExpect(jsonPath("$.roomNumber").value(roomNumber))
                .andExpect(jsonPath("$.floor").value(3))
                .andExpect(jsonPath("$.categoryRoom").value("SUITE"))
                .andExpect(jsonPath("$.status").value("Inactive"))
                .andExpect(jsonPath("$.reasonCategory").value("FLOOR_CLOSURE"))
                .andExpect(jsonPath("$.reasonDetail").value("Cierre del piso 3."))
                .andExpect(jsonPath("$.decommissionId").isNotEmpty())
                .andExpect(jsonPath("$.decommissionDateTime").value(endsWith("-05:00")));

        LocalDate today = LocalDate.now(clock);
        module2.verify(getRequestedFor(urlPathEqualTo("/api/reservations"))
                .withQueryParam("roomId", equalTo(roomId.toString()))
                .withQueryParam("dateFrom", equalTo(today.toString()))
                .withQueryParam("dateTo", equalTo(today.plusDays(365).toString())));
        assertThat(roomStatus()).isEqualTo("Inactive");
        Map<String, Object> decommission =
                jdbcTemplate.queryForMap("SELECT * FROM room_decommission WHERE room_id = ?", roomId);
        assertThat(decommission)
                .containsEntry("reason_category", "FLOOR_CLOSURE")
                .containsEntry("reason_detail", "Cierre del piso 3.")
                .containsEntry("decommissioned_by", ADMIN);
        assertThat(decommission.get("reactivated_by")).isNull();
        Map<String, Object> period = jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", roomId);
        assertThat(period)
                .containsEntry("status", "Inactive")
                .containsEntry("previous_status", "Available")
                .containsEntry("source_flow", "DECOMMISSION_ROOM")
                .containsEntry("actor_id", ADMIN);
        assertThat(period.get("start_date_time")).isEqualTo(decommission.get("decommission_date_time"));
        inventory("Available").andExpect(jsonPath("$.totalElements").value(0));
        inventory("Inactive").andExpect(jsonPath("$.items[0].roomId").value(roomId.toString()));
    }

    @Test
    @DisplayName("Sin detalle, la respuesta omite reasonDetail y la baja lo guarda nulo")
    void decommission_withoutDetail_omitsIt() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);

        decommission(ADMIN, "{\"reasonCategory\":\"PERMANENT_REMODELING\",\"reasonDetail\":\"   \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCategory").value("PERMANENT_REMODELING"))
                .andExpect(jsonPath("$.reasonDetail").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT reason_detail FROM room_decommission WHERE room_id = ?", String.class, roomId))
                .isNull();
    }

    @Test
    @DisplayName(
            "T007: una reserva vigente responde 409 RESERVATION_CONFLICT con la reserva y la habitación sigue igual")
    void decommission_withReservation_conflict() throws Exception {
        LocalDate start = LocalDate.now(clock).plusDays(200);
        givenModule2Returns("""
                {"items": [{"reservationRef": "RES-10502", "status": "ACTIVE",
                            "startDate": "%s", "endDate": "%s"}]}
                """.formatted(start, start.plusDays(3)));

        decommission(ADMIN, "{\"reasonCategory\":\"ADMINISTRATIVE_DECISION\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación tiene reservas vigentes. Deben reasignarse antes de darla de baja."))
                .andExpect(jsonPath("$.details.reservations[0].reservationRef").value("RES-10502"))
                .andExpect(jsonPath("$.details.reservations[0].startDate").value(start.toString()))
                .andExpect(jsonPath("$.details.reservations[0].status").value("ACTIVE"));

        assertUnchanged();
    }

    @Test
    @DisplayName("T009: si Módulo 2 falla responde 424 MODULE_2_UNAVAILABLE y la habitación sigue Available")
    void decommission_module2Unavailable() throws Exception {
        module2.stubFor(
                get(urlPathEqualTo("/api/reservations")).willReturn(aResponse().withStatus(503)));

        decommission(ADMIN, "{\"reasonCategory\":\"FLOOR_CLOSURE\"}")
                .andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.errorCode").value("MODULE_2_UNAVAILABLE"));

        assertUnchanged();
    }

    @Test
    @DisplayName("T010: motivo ausente o fuera de catálogo, OTHER sin detalle, detalle largo y sin X-User → 400")
    void decommission_invalidRequests() throws Exception {
        decommission(ADMIN, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Selecciona el motivo de la baja."));
        decommission(ADMIN, "{\"reasonCategory\":\"RENOVATION\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Selecciona el motivo de la baja."));
        decommission(ADMIN, "{\"reasonCategory\":\"OTHER\",\"reasonDetail\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Escribe el motivo de la baja."))
                .andExpect(jsonPath("$.details.field").value("reasonDetail"));
        decommission(ADMIN, "{\"reasonCategory\":\"OTHER\",\"reasonDetail\":\"" + "a".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El detalle admite máximo 500 caracteres."));
        decommission(null, "{\"reasonCategory\":\"FLOOR_CLOSURE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));

        module2.verify(0, getRequestedFor(urlPathEqualTo("/api/reservations")));
        assertUnchanged();
    }

    @Test
    @DisplayName("T006 y 404: una habitación ocupada o inexistente se rechaza sin consultar a Módulo 2")
    void decommission_occupiedOrUnknown() throws Exception {
        UUID occupied =
                newRoom("B" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000), RoomStatus.Occupied);

        mockMvcDecommission(occupied)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación tiene un huésped activo; debe estar Disponible para darla de baja."))
                .andExpect(jsonPath("$.details.currentStatus").value("Occupied"));
        mockMvcDecommission(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));

        module2.verify(0, getRequestedFor(urlPathEqualTo("/api/reservations")));
    }

    @Test
    @DisplayName(
            "T011: si la habitación cambia de estado mientras Módulo 2 responde, 409 al bloquear la fila y sin baja")
    void decommission_roomChangesDuringModule2Call() throws Exception {
        module2.stubFor(get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(NO_RESERVATIONS)
                        .withFixedDelay(600)));
        // La respuesta tarda bastante menos que el read-timeout de 2 s de Módulo 2; mientras tanto, otra transacción
        // pasa la habitación a InCleaning
        CompletableFuture<Void> startCleaning = CompletableFuture.runAsync(() -> {
            sleep(150);
            transactionTemplate.executeWithoutResult(tx -> {
                roomRepository.lockById(roomId);
                transitionRoomStateUseCase.transition(
                        roomId, RoomStatus.InCleaning, ADMIN, SourceFlow.START_CLEANING, null);
            });
        });

        decommission(ADMIN, "{\"reasonCategory\":\"FLOOR_CLOSURE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación cambió de estado (estado actual: En limpieza); no se dio de baja."));

        startCleaning.join();
        assertThat(roomStatus()).isEqualTo("InCleaning");
        assertThat(countDecommissions()).isZero();
    }

    @Test
    @DisplayName("T012: si falla el registro de la baja, la transición y el periodo del historial se revierten")
    void decommission_registrationFailure_rollsBack() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);
        doThrow(new IllegalStateException("fallo forzado"))
                .when(decommissionRepository)
                .save(any());

        decommission(ADMIN, "{\"reasonCategory\":\"FLOOR_CLOSURE\"}").andExpect(status().is4xxClientError());

        assertUnchanged();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId))
                .isZero();
    }

    private ResultActions decommission(String xUser, String body) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/rooms/{roomId}/decommission", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (xUser != null) {
            request.header("X-User", xUser);
        }
        return mockMvc.perform(request);
    }

    private ResultActions mockMvcDecommission(UUID target) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.post("/api/rooms/{roomId}/decommission", target)
                .header("X-User", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonCategory\":\"FLOOR_CLOSURE\"}"));
    }

    private ResultActions inventory(String status) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/api/rooms/inventory")
                .param("roomNumber", roomNumber)
                .param("status", status));
    }

    private void givenModule2Returns(String body) {
        module2.stubFor(get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void assertUnchanged() {
        assertThat(roomStatus()).isEqualTo("Available");
        assertThat(countDecommissions()).isZero();
    }

    private String roomStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, roomId);
    }

    private int countDecommissions() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_decommission WHERE room_id = ?", Integer.class, roomId);
    }

    private UUID newRoom(String number, RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(), number, 3, RoomCategory.SUITE, 4, new BigDecimal("320000"), status, null, 0))
                .getId();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
