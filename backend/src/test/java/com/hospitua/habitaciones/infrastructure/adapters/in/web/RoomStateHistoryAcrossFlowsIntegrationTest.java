package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.CompleteCleaningCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomAvailableCommand;
import com.hospitua.habitaciones.application.dto.MarkRoomReservedCommand;
import com.hospitua.habitaciones.application.dto.ReactivateRoomCommand;
import com.hospitua.habitaciones.application.dto.RegisterRoomCommand;
import com.hospitua.habitaciones.application.dto.ReportDamageCommand;
import com.hospitua.habitaciones.application.dto.StartCleaningCommand;
import com.hospitua.habitaciones.application.dto.StartRepairCommand;
import com.hospitua.habitaciones.application.ports.in.CompleteCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.CompleteRepairUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomAvailableUseCase;
import com.hospitua.habitaciones.application.ports.in.MarkRoomReservedUseCase;
import com.hospitua.habitaciones.application.ports.in.ReactivateRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.RegisterRoomUseCase;
import com.hospitua.habitaciones.application.ports.in.ReleaseCleaningTaskUseCase;
import com.hospitua.habitaciones.application.ports.in.ReportDamageUseCase;
import com.hospitua.habitaciones.application.ports.in.StartCleaningUseCase;
import com.hospitua.habitaciones.application.ports.in.StartRepairUseCase;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.ReservationTrigger;
import com.hospitua.habitaciones.domain.model.RoomCategory;
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
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Una habitación recorre todos los flujos que registran periodos y {@code GET /api/room-state-history} los muestra todos,
 * sin huecos (plan-consultar-historial-de-estados.md T015; SC-002). Usa los casos de uso de cada flujo, y los endpoints
 * de Check-In, Check-Out, bloqueo técnico y baja; Módulo 2 responde en WireMock que no hay reservas.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomStateHistoryAcrossFlowsIntegrationTest {

    private static final int MODULE2_PORT = 8082;
    private static final String ADMIN = "admin";

    private static WireMockServer module2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisterRoomUseCase registerRoomUseCase;

    @Autowired
    private MarkRoomReservedUseCase markRoomReservedUseCase;

    @Autowired
    private MarkRoomAvailableUseCase markRoomAvailableUseCase;

    @Autowired
    private StartCleaningUseCase startCleaningUseCase;

    @Autowired
    private ReleaseCleaningTaskUseCase releaseCleaningTaskUseCase;

    @Autowired
    private CompleteCleaningUseCase completeCleaningUseCase;

    @Autowired
    private ReportDamageUseCase reportDamageUseCase;

    @Autowired
    private StartRepairUseCase startRepairUseCase;

    @Autowired
    private CompleteRepairUseCase completeRepairUseCase;

    @Autowired
    private ReactivateRoomUseCase reactivateRoomUseCase;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private Clock clock;

    @BeforeAll
    static void startModule2() {
        module2 = new WireMockServer(WireMockConfiguration.wireMockConfig().port(MODULE2_PORT));
        module2.start();
        module2.stubFor(WireMock.get(urlPathEqualTo("/api/reservations"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\": []}")));
    }

    @AfterAll
    static void stopModule2() {
        module2.stop();
    }

    @Test
    @DisplayName("T015: registro, reservas, Check-In, Check-Out, limpieza, mantenimiento, baja y reactivación quedan"
            + " todos en la consulta, en orden y sin huecos entre un periodo y el siguiente")
    void history_coversEveryFlowWithoutGaps() throws Exception {
        LocalDate today = LocalDate.now(clock);
        String cleaner = newMember(StaffArea.LIMPIEZA);
        String maintainer = newMember(StaffArea.MANTENIMIENTO);
        String roomNumber = "H" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        UUID roomId = registerRoomUseCase
                .register(
                        new RegisterRoomCommand(roomNumber, 4, RoomCategory.DOBLE, 2, new BigDecimal("180000"), ADMIN))
                .roomId();

        // Reservas: se aparta, se libera y se vuelve a apartar para la llegada de hoy
        String cancelled = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        inTransaction(() -> markRoomReservedUseCase.markReserved(
                new MarkRoomReservedCommand(roomId, cancelled, ReservationTrigger.DAILY_LIST)));
        inTransaction(() -> markRoomAvailableUseCase.markAvailable(
                new MarkRoomAvailableCommand(roomId, SourceFlow.MARK_AVAILABLE, null, cancelled)));
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
                OffsetDateTime.now(clock),
                List.of(new DailyReservationRoom(roomId, roomNumber, "DOBLE", 1))));
        inTransaction(() -> markRoomReservedUseCase.markReserved(
                new MarkRoomReservedCommand(roomId, ref, ReservationTrigger.DAILY_LIST)));

        // Check-In y Check-Out
        String checkIn = mockMvc.perform(post("/api/check-in")
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationRef\":\"" + ref + "\",\"roomId\":\"" + roomId + "\",\"guests\":["
                                + "{\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"documentType\":\"CC\","
                                + "\"documentNumber\":\"123\",\"nationality\":\"Colombia\","
                                + "\"birthDate\":\"1995-03-20\",\"isReservationGuest\":true}]}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        mockMvc.perform(post("/api/check-out")
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stayId\":\"" + JsonPath.read(checkIn, "$.stayId") + "\",\"confirmed\":true}"))
                .andExpect(status().isOk());

        // Limpieza: una tarea liberada y otra terminada
        UUID released = startCleaning(roomId, cleaner);
        releaseCleaningTaskUseCase.releaseTask(released, cleaner);
        clean(roomId, cleaner);

        // Mantenimiento: daño reportado y reparado; bloqueo técnico aplicado hoy y reparado
        reportDamageUseCase.reportDamage(
                new ReportDamageCommand(roomId, "Fuga en el lavamanos.", cleaner, SourceFlow.REPORT_DAMAGE));
        repair(roomId, maintainer);
        clean(roomId, cleaner);
        mockMvc.perform(post("/api/rooms/{roomId}/technical-blocks", roomId)
                        .header("X-User", maintainer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cambio de tubería.\",\"startDate\":\"" + today
                                + "\",\"estimatedEndDate\":\"" + today.plusDays(1) + "\"}"))
                .andExpect(status().isCreated());
        repair(roomId, maintainer);
        clean(roomId, cleaner);

        // Baja y reactivación
        String decommission = mockMvc.perform(post("/api/rooms/{roomId}/decommission", roomId)
                        .header("X-User", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCategory\":\"FLOOR_CLOSURE\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        reactivateRoomUseCase.reactivate(new ReactivateRoomCommand(
                roomId, UUID.fromString(JsonPath.read(decommission, "$.decommissionId")), ADMIN));

        List<Map<String, Object>> periods = history(roomId);

        assertThat(periods)
                .extracting(row -> row.get("status"), row -> row.get("sourceFlow"))
                .containsExactly(
                        tuple("Available", "REGISTER_ROOM"),
                        tuple("Reserved", "MARK_RESERVED"),
                        tuple("Available", "MARK_AVAILABLE"),
                        tuple("Reserved", "MARK_RESERVED"),
                        tuple("Occupied", "CHECK_IN"),
                        tuple("PendingCleaning", "CHECK_OUT"),
                        tuple("InCleaning", "START_CLEANING"),
                        tuple("PendingCleaning", "RELEASE_CLEANING_TASK"),
                        tuple("InCleaning", "START_CLEANING"),
                        tuple("Available", "CONFIRM_CLEANING_END"),
                        tuple("DisabledForRepairs", "REPORT_DAMAGE"),
                        tuple("PendingCleaning", "CONFIRM_REPAIR_END"),
                        tuple("InCleaning", "START_CLEANING"),
                        tuple("Available", "CONFIRM_CLEANING_END"),
                        tuple("TechnicalBlock", "TECHNICAL_BLOCK"),
                        tuple("PendingCleaning", "CONFIRM_REPAIR_END"),
                        tuple("InCleaning", "START_CLEANING"),
                        tuple("Available", "CONFIRM_CLEANING_END"),
                        tuple("Inactive", "DECOMMISSION_ROOM"),
                        tuple("Available", "MARK_AVAILABLE"));
        assertThat(periods.stream().map(row -> row.get("sourceFlow")).distinct())
                .hasSize(SourceFlow.values().length);
        for (int i = 0; i < periods.size() - 1; i++) {
            assertThat(instant(periods.get(i), "endDateTime"))
                    .as("el periodo %d termina cuando empieza el siguiente", i + 1)
                    .isEqualTo(instant(periods.get(i + 1), "startDateTime"));
        }
        assertThat(periods.getLast().get("endDateTime")).isNull();
    }

    /** Todas las páginas de la consulta para la habitación, en el orden en que las responde la API. */
    private List<Map<String, Object>> history(UUID roomId) throws Exception {
        List<Map<String, Object>> periods = new ArrayList<>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            String body = mockMvc.perform(get("/api/room-state-history")
                            .param("roomId", roomId.toString())
                            .param("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            totalPages = JsonPath.read(body, "$.totalPages");
            periods.addAll(JsonPath.read(body, "$.items"));
        }
        return periods;
    }

    private UUID startCleaning(UUID roomId, String cleaner) {
        return startCleaningUseCase
                .startCleaning(new StartCleaningCommand(roomId, cleaner))
                .taskId();
    }

    private void clean(UUID roomId, String cleaner) {
        completeCleaningUseCase.completeCleaning(
                new CompleteCleaningCommand(startCleaning(roomId, cleaner), cleaner, null));
    }

    private void repair(UUID roomId, String maintainer) {
        UUID task = startRepairUseCase
                .startRepair(new StartRepairCommand(roomId, maintainer))
                .taskId();
        completeRepairUseCase.completeRepair(task, maintainer);
    }

    private void inTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private static OffsetDateTime instant(Map<String, Object> row, String field) {
        return OffsetDateTime.parse((String) row.get(field));
    }

    private String newMember(StaffArea area) {
        String username = area.name().substring(0, 4).toLowerCase() + "_"
                + UUID.randomUUID().toString().substring(0, 8);
        staffAccountRepository.save(StaffAccount.createNew(
                username, "$2a$10$hash", "Personal " + area.name(), area, OffsetDateTime.now(clock), ADMIN));
        return username;
    }
}
