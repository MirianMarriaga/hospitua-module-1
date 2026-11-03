package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.in.ApplyScheduledTechnicalBlocksUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * {@code POST /api/rooms/{roomId}/technical-blocks}, {@code GET …/current} y {@code runDaily} sobre la aplicación completa, PostgreSQL real y
 * Módulo 2 en WireMock (plan-programar-bloqueo-tecnico-para-habitacion.md T013, T014, T023 y casos de T006 a T012 y T015; T018
 * de plan-consultar-informacion-mantenimientos.md). El worker del Outbox está apagado en el perfil de pruebas, así que
 * el aviso se revisa en {@code outbox_notification}. El espía del repositorio de informes (T013) le da a esta clase su
 * propio contexto; la base es compartida y cada prueba crea sus habitaciones.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechnicalBlockSchedulingIntegrationTest {

    private static final int MODULE2_PORT = 8082;
    private static final String NO_RESERVATIONS = "{\"items\": []}";
    private static final String REASON = "Cambio preventivo del sistema de aire acondicionado.";

    private static WireMockServer module2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplyScheduledTechnicalBlocksUseCase applyScheduledTechnicalBlocks;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @MockitoSpyBean
    private TechnicalBlockReportRepositoryPort reportRepository;

    private LocalDate today;
    private String member;
    private UUID roomId;

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
        today = LocalDate.now(clock);
        member = newMaintenanceMember();
        roomId = newRoom(RoomStatus.Available);
    }

    @AfterEach
    void resetSpy() {
        reset(reportRepository);
    }

    @Test
    @DisplayName("T007 y T018: inicio futuro → 201 Scheduled, sin cambio de estado ni historial, con su aviso; desde"
            + " ese momento la consulta de mantenimientos lo reporta")
    void futureStart_scheduled() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);
        LocalDate start = today.plusDays(20);

        String id = createdId(schedule(member, body(REASON, start, start.plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Scheduled")));

        module2.verify(getRequestedFor(urlPathEqualTo("/api/reservations"))
                .withQueryParam("roomId", equalTo(roomId.toString()))
                .withQueryParam("dateFrom", equalTo(start.toString()))
                .withQueryParam("dateTo", equalTo(start.plusDays(2).toString())));
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM technical_block_report WHERE id = ?::uuid", id))
                .containsEntry("room_id", roomId)
                .containsEntry("maintenance_staff_member_id", member)
                .containsEntry("technical_block_reason", REASON)
                .containsEntry("status", "Scheduled");
        assertThat(roomStatus()).isEqualTo("Available");
        assertThat(historyRows()).isZero();
        assertNotice(id, start, start.plusDays(2));
        mockMvc.perform(MockMvcRequestBuilders.get("/api/rooms/{roomId}/maintenances", roomId)
                        .param("startDate", start.plusDays(1).toString())
                        .param("endDate", start.plusDays(5).toString()))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.conflicts[0].maintenanceStart").value(start.toString()));
    }

    @Test
    @DisplayName("T006: inicio hoy sobre Available → 201 Applied, TechnicalBlock con el periodo del miembro y un aviso")
    void startToday_applied() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);

        String id = createdId(schedule(member, body(REASON, today, today.plusDays(1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Applied")));

        assertThat(roomStatus()).isEqualTo("TechnicalBlock");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM technical_block_report WHERE id = ?::uuid", String.class, id))
                .isEqualTo("Applied");
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", roomId))
                .containsEntry("status", "TechnicalBlock")
                .containsEntry("previous_status", "Available")
                .containsEntry("source_flow", "TECHNICAL_BLOCK")
                .containsEntry("actor_id", member);
        assertNotice(id, today, today.plusDays(1));
    }

    @Test
    @DisplayName("T008 y T013: una reserva en el rango → 409 RESERVATION_CONFLICT y no se registra nada")
    void reservationConflict_nothingRegistered() throws Exception {
        LocalDate start = today.plusDays(30);
        givenModule2Returns("""
                {"items": [{"reservationRef": "RES-10502", "status": "ACTIVE",
                            "startDate": "%s", "endDate": "%s"}]}
                """.formatted(start.plusDays(1), start.plusDays(4)));

        schedule(member, body(REASON, start, start.plusDays(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_CONFLICT"))
                .andExpect(jsonPath("$.details.reservations[0].reservationRef").value("RES-10502"));

        assertNothingRegistered();
    }

    @Test
    @DisplayName("T013: si Módulo 2 falla → 424 MODULE_2_UNAVAILABLE y no se registra nada")
    void module2Unavailable_nothingRegistered() throws Exception {
        module2.stubFor(
                get(urlPathEqualTo("/api/reservations")).willReturn(aResponse().withStatus(503)));

        schedule(member, body(REASON, today, today.plusDays(1)))
                .andExpect(status().isFailedDependency())
                .andExpect(jsonPath("$.errorCode").value("MODULE_2_UNAVAILABLE"));

        assertNothingRegistered();
    }

    @Test
    @DisplayName("T013: si falla el registro del informe, la transición y el periodo de hoy se revierten sin aviso")
    void persistenceFailure_rollsBack() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);
        doThrow(new IllegalStateException("fallo forzado"))
                .when(reportRepository)
                .save(any());

        schedule(member, body(REASON, today, today.plusDays(1))).andExpect(status().is4xxClientError());

        assertNothingRegistered();
    }

    @Test
    @DisplayName("T009: un mantenimiento que se cruza → 409 MAINTENANCE_CONFLICT con las fechas, sin aviso nuevo")
    void maintenanceConflict() throws Exception {
        givenModule2Returns(NO_RESERVATIONS);
        LocalDate start = today.plusDays(40);
        schedule(member, body(REASON, start, start.plusDays(3))).andExpect(status().isCreated());

        schedule(member, body(REASON, start.plusDays(2), start.plusDays(6)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("MAINTENANCE_CONFLICT"))
                .andExpect(jsonPath("$.details.conflicts[0].maintenanceStart").value(start.toString()))
                .andExpect(jsonPath("$.details.conflicts[0].maintenanceEnd")
                        .value(start.plusDays(3).toString()));

        assertThat(reportRows()).isEqualTo(1);
        assertThat(noticeRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("T014: dos programaciones simultáneas con rangos solapados: solo una se registra")
    void concurrentSchedules_onlyOneRegistered() throws Exception {
        // Módulo 2 tarda un poco, así las dos pasan su consulta y compiten por la fila bloqueada
        module2.stubFor(get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(NO_RESERVATIONS)
                        .withFixedDelay(300)));
        LocalDate start = today.plusDays(50);
        Callable<Integer> first = () -> schedule(member, body(REASON, start, start.plusDays(4)))
                .andReturn()
                .getResponse()
                .getStatus();
        Callable<Integer> second = () -> schedule(member, body(REASON, start.plusDays(2), start.plusDays(6)))
                .andReturn()
                .getResponse()
                .getStatus();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = executor.invokeAll(List.of(first, second));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(reportRows()).isEqualTo(1);
        assertThat(noticeRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("T010 a T012 y CURRENT_USER_REQUIRED: entradas inválidas se rechazan sin consultar a Módulo 2")
    void invalidRequests_noModule2Call() throws Exception {
        schedule(member, body("   ", today.plusDays(1), today.plusDays(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Escribe el motivo del mantenimiento (máximo 500 caracteres)."))
                .andExpect(jsonPath("$.details.field").value("reason"));
        schedule(
                        member,
                        "{\"reason\":\"" + REASON + "\",\"startDate\":\"2026-02-31\",\"estimatedEndDate\":\""
                                + today.plusDays(2) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("startDate"));
        schedule(member, "{\"reason\":\"" + REASON + "\",\"startDate\":\"" + today.plusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("estimatedEndDate"));
        schedule(member, body(REASON, today.minusDays(1), today.plusDays(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.details.rule").value("START_IN_PAST"));
        schedule(null, body(REASON, today.plusDays(1), today.plusDays(2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        roomId = newRoom(RoomStatus.Inactive);
        schedule(member, body(REASON, today.plusDays(1), today.plusDays(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("Inactive"));
        roomId = newRoom(RoomStatus.Occupied);
        schedule(member, body(REASON, today, today.plusDays(2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Hoy la habitación está Ocupada. Para empezar hoy debe estar disponible; elige una"
                                + " fecha de inicio posterior."));
        roomId = UUID.randomUUID();
        schedule(member, body(REASON, today.plusDays(1), today.plusDays(2)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));

        module2.verify(0, getRequestedFor(urlPathEqualTo("/api/reservations")));
    }

    @Test
    @DisplayName("T015: runDaily aplica el Scheduled que cubre hoy sobre Available (periodo sin actor) y caduca el"
            + " que ya terminó; repetirlo no cambia nada")
    void runDaily_appliesAndExpires() {
        TechnicalBlockReport covering = reportRepository.save(TechnicalBlockReport.schedule(
                roomId, member, REASON, today.minusDays(1), today.plusDays(1), OffsetDateTime.now(clock)));
        UUID occupied = newRoom(RoomStatus.Occupied);
        TechnicalBlockReport waiting = reportRepository.save(TechnicalBlockReport.schedule(
                occupied, member, REASON, today, today.plusDays(2), OffsetDateTime.now(clock)));
        TechnicalBlockReport ended = reportRepository.save(TechnicalBlockReport.schedule(
                newRoom(RoomStatus.Available),
                member,
                REASON,
                today.minusDays(4),
                today.minusDays(2),
                OffsetDateTime.now(clock)));

        applyScheduledTechnicalBlocks.runDaily(today);
        applyScheduledTechnicalBlocks.runDaily(today);

        assertThat(reportStatus(covering)).isEqualTo("Applied");
        assertThat(roomStatus()).isEqualTo("TechnicalBlock");
        Map<String, Object> period = jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", roomId);
        assertThat(period).containsEntry("source_flow", "TECHNICAL_BLOCK").containsEntry("status", "TechnicalBlock");
        assertThat(period.get("actor_id")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId))
                .isEqualTo(1);
        assertThat(reportStatus(waiting)).isEqualTo("Scheduled");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, occupied))
                .isEqualTo("Occupied");
        assertThat(reportStatus(ended)).isEqualTo("Expired");
    }

    @Test
    @DisplayName("T023: Ver informe devuelve el Scheduled más temprano con el nombre del responsable, luego el Applied;"
            + " sin informe responde 404 TECHNICAL_BLOCK_NOT_FOUND")
    void currentTechnicalBlock() throws Exception {
        current()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("TECHNICAL_BLOCK_NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value("Esta habitación no tiene un mantenimiento programado ni en curso."));
        givenModule2Returns(NO_RESERVATIONS);
        LocalDate start = today.plusDays(60);
        schedule(member, body("Segundo mantenimiento.", start.plusDays(10), start.plusDays(11)))
                .andExpect(status().isCreated());
        schedule(member, body(REASON, start, start.plusDays(2))).andExpect(status().isCreated());

        current()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value(REASON))
                .andExpect(jsonPath("$.responsibleName").value("Personal de mantenimiento"))
                .andExpect(jsonPath("$.startDate").value(start.toString()))
                .andExpect(
                        jsonPath("$.estimatedEndDate").value(start.plusDays(2).toString()))
                .andExpect(jsonPath("$.roomNumber").exists())
                .andExpect(jsonPath("$.reportDateTime").exists());

        schedule(member, body("Arreglo urgente.", today, today)).andExpect(status().isCreated());
        current()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Arreglo urgente."))
                .andExpect(jsonPath("$.startDate").value(today.toString()));

        roomId = UUID.randomUUID();
        current()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
    }

    private ResultActions current() throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/api/rooms/{roomId}/technical-blocks/current", roomId));
    }

    private ResultActions schedule(String xUser, String body) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(
                        "/api/rooms/{roomId}/technical-blocks", roomId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (xUser != null) {
            request.header("X-User", xUser);
        }
        return mockMvc.perform(request);
    }

    private static String body(String reason, LocalDate start, LocalDate end) {
        return "{\"reason\":\"" + reason + "\",\"startDate\":\"" + start + "\",\"estimatedEndDate\":\"" + end + "\"}";
    }

    private static String createdId(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.technicalBlockId");
    }

    private void givenModule2Returns(String body) {
        module2.stubFor(get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    /** Un solo aviso SCHEDULED del informe, con la categoría de la habitación y el rango programado. */
    private void assertNotice(String reportId, LocalDate start, LocalDate end) {
        assertThat(noticeRows()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT payload->>'action' AS action, payload->>'categoryRoom' AS category_room, "
                                + "payload->>'startDate' AS start_date, payload->>'endDate' AS end_date "
                                + "FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                                + "AND payload->>'maintenanceId' = ?",
                        reportId))
                .containsEntry("action", "SCHEDULED")
                .containsEntry("category_room", "DOBLE")
                .containsEntry("start_date", start.toString())
                .containsEntry("end_date", end.toString());
    }

    /** Sin informe, la habitación sigue Available, sin periodo nuevo y sin aviso de mantenimiento. */
    private void assertNothingRegistered() {
        assertThat(reportRows()).isZero();
        assertThat(roomStatus()).isEqualTo("Available");
        assertThat(historyRows()).isZero();
        assertThat(noticeRows()).isZero();
    }

    private String reportStatus(TechnicalBlockReport report) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM technical_block_report WHERE id = ?", String.class, report.getId());
    }

    private int reportRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM technical_block_report WHERE room_id = ?", Integer.class, roomId);
    }

    private int historyRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId);
    }

    private int noticeRows() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                        + "AND payload->>'roomId' = ?",
                Integer.class,
                roomId.toString());
    }

    private String roomStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, roomId);
    }

    private UUID newRoom(RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "K" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        2,
                        RoomCategory.DOBLE,
                        2,
                        new BigDecimal("150000"),
                        status,
                        null,
                        0))
                .getId();
    }

    private String newMaintenanceMember() {
        String username = "tb_" + UUID.randomUUID().toString().substring(0, 8);
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
