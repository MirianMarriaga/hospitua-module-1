package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.application.ports.in.ReportDamageUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.exception.InvalidRoomTransitionException;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code POST /api/rooms/{roomId}/damage-reports} y {@code GET …/latest} sobre la aplicación completa y PostgreSQL real
 * (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T008, T009, T013, T015). El worker del Outbox está apagado
 * en el perfil de pruebas, así que el aviso se revisa en {@code outbox_notification}. La base es compartida: cada prueba
 * crea sus habitaciones y sus cuentas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DamageReportIntegrationTest {

    private static final String DESCRIPTION = "Ventana con el vidrio fisurado.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReportDamageUseCase reportDamageUseCase;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("T013: responde 201; la habitación queda inhabilitada con su reporte, su periodo y el aviso SCHEDULED")
    void report_disablesRoom() throws Exception {
        Room room = newRoom(RoomStatus.Available);
        String author = newMember(StaffArea.LIMPIEZA);

        String reportId = JsonPath.read(
                report(author, room.getId(), "{\"damageDescription\":\"" + DESCRIPTION + "\"}")
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.damageReportId");

        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.DisabledForRepairs);
        Map<String, Object> report =
                jdbcTemplate.queryForMap("SELECT * FROM damage_report WHERE id = ?::uuid", reportId);
        assertThat(report)
                .containsEntry("room_id", room.getId())
                .containsEntry("author_id", author)
                .containsEntry("damage_description", DESCRIPTION);
        Map<String, Object> period = jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", room.getId());
        assertThat(period)
                .containsEntry("status", "DisabledForRepairs")
                .containsEntry("previous_status", "Available")
                .containsEntry("source_flow", "REPORT_DAMAGE")
                .containsEntry("actor_id", author)
                .containsEntry("start_date_time", report.get("report_date_time"));
        String today = LocalDate.now(clock).toString();
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT payload->>'action' AS action, payload->>'roomId' AS room_id, "
                                + "payload->>'categoryRoom' AS category_room, payload->>'startDate' AS start_date, "
                                + "payload->>'endDate' AS end_date FROM outbox_notification "
                                + "WHERE event_type = 'MAINTENANCE_NOTICE' AND payload->>'maintenanceId' = ?",
                        reportId))
                .containsEntry("action", "SCHEDULED")
                .containsEntry("room_id", room.getId().toString())
                .containsEntry("category_room", "SENCILLA")
                .containsEntry("start_date", today)
                .containsEntry("end_date", today);
    }

    @Test
    @DisplayName("T007: una fecha enviada por el cliente se ignora")
    void report_ignoresClientDate() throws Exception {
        Room room = newRoom(RoomStatus.Available);

        report(
                        newMember(StaffArea.MANTENIMIENTO),
                        room.getId(),
                        "{\"damageDescription\":\"" + DESCRIPTION
                                + "\",\"reportDateTime\":\"2000-01-01T00:00:00-05:00\"}")
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT report_date_time > now() - interval '1 hour' FROM damage_report WHERE room_id = ?",
                        Boolean.class,
                        room.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("T005: una descripción en blanco o de más de 500 caracteres responde 400 VALIDATION_ERROR sin cambios")
    void report_invalidDescription() throws Exception {
        Room room = newRoom(RoomStatus.Available);
        String author = newMember(StaffArea.LIMPIEZA);

        for (String description : List.of("   ", "a".repeat(501))) {
            report(author, room.getId(), "{\"damageDescription\":\"" + description + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.message")
                            .value("La descripción del daño es obligatoria (máximo 500 caracteres)."))
                    .andExpect(jsonPath("$.details.field").value("damageDescription"));
        }

        assertUnchanged(room.getId());
    }

    @Test
    @DisplayName("T006: una habitación Occupied responde 409 ROOM_INVALID_STATE con su estado y no crea el reporte")
    void report_occupiedRoom() throws Exception {
        Room room = newRoom(RoomStatus.Occupied);

        report(newMember(StaffArea.LIMPIEZA), room.getId(), "{\"damageDescription\":\"" + DESCRIPTION + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("Occupied"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación está Ocupada; ahora no se puede reportar un daño en ella."));

        assertThat(countReports(room.getId())).isZero();
    }

    @Test
    @DisplayName("Una habitación inexistente responde 404 ROOM_NOT_FOUND, y sin X-User 400 CURRENT_USER_REQUIRED")
    void report_roomNotFoundAndNoCurrentUser() throws Exception {
        report(newMember(StaffArea.LIMPIEZA), UUID.randomUUID(), "{\"damageDescription\":\"" + DESCRIPTION + "\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
        mockMvc.perform(post(
                                "/api/rooms/{roomId}/damage-reports",
                                newRoom(RoomStatus.Available).getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"damageDescription\":\"" + DESCRIPTION + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
    }

    @Test
    @DisplayName("T008: de dos reportes simultáneos solo uno gana; el otro ve DisabledForRepairs tras el bloqueo")
    void report_concurrent() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available).getId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (String author : List.of("mtorres", "jramirez")) {
                Callable<Object> attempt = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        return reportDamageUseCase.reportDamage(
                                new ReportDamageCommand(roomId, DESCRIPTION, author, SourceFlow.REPORT_DAMAGE));
                    } catch (InvalidRoomTransitionException ex) {
                        return ex;
                    }
                };
                results.add(executor.submit(attempt));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(outcomes)
                    .filteredOn(InvalidRoomTransitionException.class::isInstance)
                    .singleElement()
                    .extracting(ex -> ((InvalidRoomTransitionException) ex).getCurrentStatus())
                    .isEqualTo(RoomStatus.DisabledForRepairs);
            assertThat(countReports(roomId)).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("T009: si falla el guardado del reporte se revierte la transición, sin aviso")
    void report_saveFailureRollsBack() {
        UUID roomId = newRoom(RoomStatus.Available).getId();

        assertThatThrownBy(() -> reportDamageUseCase.reportDamage(
                        new ReportDamageCommand(roomId, DESCRIPTION, tooLongAuthor(), SourceFlow.REPORT_DAMAGE)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertUnchanged(roomId);
    }

    @Test
    @DisplayName("T009: invocado desde una transacción externa, la falla revierte también lo que esa transacción hizo")
    void report_saveFailureRollsBackOuterTransaction() {
        UUID first = newRoom(RoomStatus.Available).getId();
        UUID second = newRoom(RoomStatus.Available).getId();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    reportDamageUseCase.reportDamage(
                            new ReportDamageCommand(first, DESCRIPTION, "mtorres", SourceFlow.CONFIRM_CLEANING_END));
                    reportDamageUseCase.reportDamage(new ReportDamageCommand(
                            second, DESCRIPTION, tooLongAuthor(), SourceFlow.CONFIRM_CLEANING_END));
                }))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertUnchanged(first);
        assertUnchanged(second);
    }

    @Test
    @DisplayName("T013: Ver informe devuelve el reporte con el nombre completo del autor")
    void latest_returnsReport() throws Exception {
        Room room = newRoom(RoomStatus.Available);
        String author = newMember(StaffArea.MANTENIMIENTO);
        report(author, room.getId(), "{\"damageDescription\":\"" + DESCRIPTION + "\"}")
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get("/api/rooms/{roomId}/damage-reports/latest", room.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.damageDescription").value(DESCRIPTION))
                .andExpect(jsonPath("$.authorName").value("Personal de mantenimiento"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String reportDateTime = JsonPath.read(body, "$.reportDateTime");
        assertThat(reportDateTime).endsWith("-05:00");
    }

    @Test
    @DisplayName("T013: Ver informe de una habitación que no está inhabilitada responde 404 DAMAGE_REPORT_NOT_FOUND")
    void latest_notDisabled() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available).getId();

        mockMvc.perform(get("/api/rooms/{roomId}/damage-reports/latest", roomId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("DAMAGE_REPORT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Esta habitación no tiene un daño reportado."))
                .andExpect(jsonPath("$.details.currentStatus").value("Available"));
        mockMvc.perform(get("/api/rooms/{roomId}/damage-reports/latest", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("T015: tras el reporte, la habitación ya no aparece en el panel de limpieza")
    void report_leavesCleaningPanel() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available).getId();
        String member = newMember(StaffArea.LIMPIEZA);
        assertThat(cleaningPanelRoomIds(member)).contains(roomId.toString());

        report(member, roomId, "{\"damageDescription\":\"" + DESCRIPTION + "\"}")
                .andExpect(status().isCreated());

        assertThat(cleaningPanelRoomIds(member)).doesNotContain(roomId.toString());
    }

    private ResultActions report(String xUser, UUID roomId, String body) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/damage-reports", roomId)
                .header("X-User", xUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<String> cleaningPanelRoomIds(String member) throws Exception {
        return JsonPath.read(
                mockMvc.perform(get("/api/cleaning/panel").header("X-User", member))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.rooms[*].roomId");
    }

    /** La habitación sigue Available, sin reporte, sin periodo nuevo y sin aviso de mantenimiento. */
    private void assertUnchanged(UUID roomId) {
        assertThat(roomRepository.findById(roomId).orElseThrow().getStatus()).isEqualTo(RoomStatus.Available);
        assertThat(countReports(roomId)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_state_history WHERE room_id = ?", Integer.class, roomId))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_notification WHERE event_type = 'MAINTENANCE_NOTICE' "
                                + "AND payload->>'roomId' = ?",
                        Integer.class,
                        roomId.toString()))
                .isZero();
    }

    private int countReports(UUID roomId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM damage_report WHERE room_id = ?", Integer.class, roomId);
    }

    /** {@code author_id} admite 100 caracteres: forzar la falla al guardar el reporte, después de la transición. */
    private static String tooLongAuthor() {
        return "a".repeat(101);
    }

    private Room newRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                "R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.SENCILLA,
                2,
                new BigDecimal("150000"),
                status,
                null,
                0));
    }

    private String newMember(StaffArea area) {
        String username = "dmg_" + UUID.randomUUID().toString().substring(0, 8);
        String fullName = area == StaffArea.LIMPIEZA ? "Personal de limpieza" : "Personal de mantenimiento";
        staffAccountRepository.save(
                StaffAccount.createNew(username, "$2a$10$hash", fullName, area, OffsetDateTime.now(), "admin"));
        return username;
    }
}
