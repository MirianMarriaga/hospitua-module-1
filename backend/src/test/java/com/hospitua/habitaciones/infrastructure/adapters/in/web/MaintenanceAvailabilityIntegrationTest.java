package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.MaintenanceAvailabilityDto.ConflictDto;
import com.hospitua.habitaciones.application.ports.in.ConsultMaintenanceAvailabilityUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.TechnicalBlockReportRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/rooms/{roomId}/maintenances} sobre la aplicación completa y PostgreSQL real
 * (plan-consultar-informacion-mantenimientos.md T010, T014 a T017). La base es compartida: cada prueba crea sus
 * habitaciones e informes, y las fechas salen de {@code Clock}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaintenanceAvailabilityIntegrationTest {

    private static final String FORMAT_MESSAGE =
            "La habitación y las fechas deben tener un formato válido (fechas AAAA-MM-DD).";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConsultMaintenanceAvailabilityUseCase consultMaintenanceAvailability;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private TechnicalBlockReportRepositoryPort reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
    }

    @Test
    @DisplayName("T014: con cruce responde exactamente roomId, available y conflicts, sin la justificación")
    void overlap_exactContract() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);
        schedule(roomId, 4, 8);

        consult(roomId, today.plusDays(2), today.plusDays(6))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"roomId\":\"" + roomId + "\",\"available\":false,\"conflicts\":["
                                        + "{\"maintenanceStart\":\"" + today.plusDays(4) + "\",\"maintenanceEnd\":\""
                                        + today.plusDays(8) + "\"}]}",
                                JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("T010: la consulta no modifica datos ni escribe en el historial")
    void consult_readOnly() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);
        schedule(roomId, 1, 2);
        int history = count("room_state_history");
        int audit = count("room_audit_log");
        int reports = count("technical_block_report");

        consult(roomId, today, today.plusDays(3)).andExpect(status().isOk());

        assertThat(count("room_state_history")).isEqualTo(history);
        assertThat(count("room_audit_log")).isEqualTo(audit);
        assertThat(count("technical_block_report")).isEqualTo(reports);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM room WHERE id = ?", String.class, roomId))
                .isEqualTo("Available");
    }

    @Test
    @DisplayName("T015: un mantenimiento que termina el día anterior a la llegada no es cruce")
    void endsBeforeArrival_available() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);
        schedule(roomId, 1, 3);

        consult(roomId, today.plusDays(4), today.plusDays(6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.conflicts").isEmpty());
    }

    @Test
    @DisplayName("T016: si empieza el día de la salida no es cruce para la estadía, pero sí con el último día incluido")
    void startsOnDeparture() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);
        schedule(roomId, 5, 7);

        consult(roomId, today.plusDays(2), today.plusDays(5))
                .andExpect(jsonPath("$.available").value(true));
        assertThat(consultMaintenanceAvailability
                        .consult(roomId, today.plusDays(2), today.plusDays(5))
                        .conflicts())
                .containsExactly(new ConflictDto(today.plusDays(5), today.plusDays(7)));
    }

    @Test
    @DisplayName("Llegada igual a la salida evalúa ese día")
    void sameDayStay_evaluatesThatDay() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);
        schedule(roomId, 3, 3);

        consult(roomId, today.plusDays(3), today.plusDays(3))
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    @DisplayName("T017: DisabledForRepairs con llegada hoy → cruce de hoy; con llegada mañana → disponible")
    void disabledForRepairs_onlyToday() throws Exception {
        UUID roomId = newRoom(RoomStatus.DisabledForRepairs);

        assertTodayOnly(roomId);
    }

    @Test
    @DisplayName("T017: TechnicalBlock con el informe vencido, igual que DisabledForRepairs")
    void overdueTechnicalBlock_onlyToday() throws Exception {
        UUID roomId = newRoom(RoomStatus.TechnicalBlock);
        TechnicalBlockReport overdue = TechnicalBlockReport.schedule(
                roomId, "mtorres", "Cambio de tubería.", today.minusDays(5), today.minusDays(1), reportedAt());
        overdue.apply();
        reportRepository.save(overdue);

        assertTodayOnly(roomId);
    }

    @Test
    @DisplayName("Salida anterior a la llegada → 400 INVALID_DATE_RANGE con details.rule = END_BEFORE_START")
    void departureBeforeArrival() throws Exception {
        consult(newRoom(RoomStatus.Available), today.plusDays(5), today.plusDays(3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DATE_RANGE"))
                .andExpect(jsonPath("$.message").value("La fecha de fin no puede ser anterior a la fecha de inicio."))
                .andExpect(jsonPath("$.details.rule").value("END_BEFORE_START"));
    }

    @Test
    @DisplayName("Un rango que termina antes de hoy → 400 INVALID_DATE_RANGE con details.rule = RANGE_IN_PAST")
    void rangeInPast() throws Exception {
        consult(newRoom(RoomStatus.Available), today.minusDays(5), today.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.rule").value("RANGE_IN_PAST"));
    }

    @Test
    @DisplayName("T014: roomId sin formato de UUID → 400 VALIDATION_ERROR con details.field = roomId")
    void invalidRoomId() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/maintenances", "no-es-uuid")
                        .param("startDate", today.toString())
                        .param("endDate", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(FORMAT_MESSAGE))
                .andExpect(jsonPath("$.details.field").value("roomId"));
    }

    @Test
    @DisplayName("T014: fecha inexistente (2026-02-31), otro formato o falta → 400 VALIDATION_ERROR por campo")
    void invalidDates() throws Exception {
        UUID roomId = newRoom(RoomStatus.Available);

        mockMvc.perform(get("/api/rooms/{roomId}/maintenances", roomId)
                        .param("startDate", "2026-02-31")
                        .param("endDate", "2026-03-02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(FORMAT_MESSAGE))
                .andExpect(jsonPath("$.details.field").value("startDate"));
        mockMvc.perform(get("/api/rooms/{roomId}/maintenances", roomId)
                        .param("startDate", today.toString())
                        .param("endDate", "31-12-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("endDate"));
        mockMvc.perform(get("/api/rooms/{roomId}/maintenances", roomId).param("startDate", today.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FORMAT_MESSAGE))
                .andExpect(jsonPath("$.details.field").value("endDate"));
    }

    @Test
    @DisplayName("Habitación inexistente → 404 ROOM_NOT_FOUND")
    void unknownRoom() throws Exception {
        consult(UUID.randomUUID(), today, today.plusDays(1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró la habitación."));
    }

    private void assertTodayOnly(UUID roomId) throws Exception {
        consult(roomId, today, today.plusDays(2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.conflicts.length()").value(1))
                .andExpect(jsonPath("$.conflicts[0].maintenanceStart").value(today.toString()))
                .andExpect(jsonPath("$.conflicts[0].maintenanceEnd").value(today.toString()));
        consult(roomId, today.plusDays(1), today.plusDays(3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    private ResultActions consult(UUID roomId, LocalDate arrival, LocalDate departure) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}/maintenances", roomId)
                .param("startDate", arrival.toString())
                .param("endDate", departure.toString()));
    }

    private void schedule(UUID roomId, int fromDays, int toDays) {
        reportRepository.save(TechnicalBlockReport.schedule(
                roomId,
                "mtorres",
                "Justificación que no debe salir en la respuesta.",
                today.plusDays(fromDays),
                today.plusDays(toDays),
                reportedAt()));
    }

    private OffsetDateTime reportedAt() {
        return OffsetDateTime.now(clock);
    }

    private UUID newRoom(RoomStatus status) {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "M" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        3,
                        RoomCategory.DOBLE,
                        2,
                        new BigDecimal("150000"),
                        status,
                        null,
                        0))
                .getId();
    }

    private int count(String table) {
        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return rows == null ? 0 : rows;
    }
}
