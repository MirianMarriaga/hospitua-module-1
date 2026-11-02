package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/rooms/{roomId}/base-rate} sobre la aplicación completa y PostgreSQL real (plan-consultar-tarifa-base.md
 * T006, T011, T012, T014, T015). Solo lee habitaciones de la semilla.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomBaseRateIntegrationTest {

    /** Habitación 105 de la semilla: SUITE, tarifa 320000.00. */
    private static final String SUITE_105 = "a0000000-0000-0000-0000-000000000105";
    /** Habitación 104 de la semilla, Inactive. */
    private static final String INACTIVE_104 = "a0000000-0000-0000-0000-000000000104";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T006 y T014: exactamente roomId, date tal cual, baseRate como texto con dos decimales y COP")
    void baseRate_exactContract() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", SUITE_105).param("date", "2026-10-08"))
                .andExpect(status().isOk())
                .andExpect(content()
                        .json(
                                "{\"roomId\":\"" + SUITE_105
                                        + "\",\"date\":\"2026-10-08\",\"baseRate\":\"320000.00\",\"currency\":\"COP\"}",
                                JsonCompareMode.STRICT));
        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", SUITE_105).param("date", "2027-01-01"))
                .andExpect(jsonPath("$.date").value("2027-01-01"))
                .andExpect(jsonPath("$.baseRate").value("320000.00"));
    }

    @Test
    @DisplayName(
            "FR-005 y T015: responde también con una habitación Inactive y no escribe en el historial ni la bitácora")
    void baseRate_inactiveRoom_noWrites() throws Exception {
        int history = count("room_state_history");
        int audit = count("room_audit_log");

        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", INACTIVE_104).param("date", "2026-10-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseRate").value("200000.00"));

        assertThat(count("room_state_history")).isEqualTo(history);
        assertThat(count("room_audit_log")).isEqualTo(audit);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM room WHERE id = ?::uuid", String.class, INACTIVE_104))
                .isEqualTo("Inactive");
    }

    @Test
    @DisplayName("T011: una habitación inexistente responde 404 ROOM_NOT_FOUND con ApiError")
    void baseRate_unknownRoom() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", "11111111-2222-4333-8444-555555555555")
                        .param("date", "2026-10-08"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No se encontró la habitación."))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/rooms/11111111-2222-4333-8444-555555555555/base-rate"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "08-10-2026", "2026/10/08", "2026-02-31", "2026-13-01"})
    @DisplayName("T012: fecha con otro formato o inexistente → 400 VALIDATION_ERROR con details.field = date")
    void baseRate_invalidDate(String date) throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", SUITE_105).param("date", date))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("La fecha debe tener un formato válido (AAAA-MM-DD)."))
                .andExpect(jsonPath("$.details.field").value("date"));
    }

    @Test
    @DisplayName("T012: sin date → 400 VALIDATION_ERROR con details.field = date")
    void baseRate_missingDate() throws Exception {
        mockMvc.perform(get("/api/rooms/{roomId}/base-rate", SUITE_105))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha debe tener un formato válido (AAAA-MM-DD)."))
                .andExpect(jsonPath("$.details.field").value("date"));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
