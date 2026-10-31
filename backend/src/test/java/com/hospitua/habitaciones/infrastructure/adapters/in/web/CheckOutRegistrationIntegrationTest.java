package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * {@code POST /api/check-out} sobre la aplicación completa y PostgreSQL real (plan-registrar-check-out.md T006, T008,
 * T013–T016). Cada prueba parte de un Check-In real ({@code POST /api/check-in}), así la estancia, sus huéspedes y el
 * historial son los que deja ese flujo. Módulo 3 no corre: el Check-Out no lo consulta (T016). El worker del Outbox está
 * apagado en el perfil de pruebas, así que los mensajes se revisan en {@code outbox_notification}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckOutRegistrationIntegrationTest {

    private static final String RECEPTIONIST = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private String ref;
    private Room room;
    private String stayId;

    @BeforeEach
    void checkIn() throws Exception {
        today = LocalDate.now(clock);
        ref = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        room = roomRepository.save(new Room(
                UUID.randomUUID(),
                "O" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                3,
                new BigDecimal("200000"),
                RoomStatus.Reserved,
                ref,
                0));
        reservationRepository.save(new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123",
                "Colombia",
                "BOOKING",
                today,
                today.plusDays(2),
                2,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 2))));
        stayId = mockMvc.perform(post("/api/check-in")
                        .header("X-User", RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationRef\":\"" + ref + "\",\"roomId\":\"" + room.getId() + "\",\"guests\":["
                                + "{\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"documentType\":\"CC\","
                                + "\"documentNumber\":\"123\",\"nationality\":\"Colombia\",\"birthDate\":\"1995-03-20\","
                                + "\"isReservationGuest\":true},"
                                + "{\"firstName\":\"John\",\"lastName\":\"Smith\",\"documentType\":\"PAS\","
                                + "\"documentNumber\":\"X99\",\"nationality\":\"Estados Unidos\","
                                + "\"birthDate\":\"1990-05-12\",\"originPlace\":\"Miami, Estados Unidos\","
                                + "\"destinationPlace\":\"Cartagena, Colombia\"}]}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"stayId\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName(
            "T008: responde 200, cierra la estancia y deja la habitación PendingCleaning con su historial y auditoría")
    void checkOut_closesStayAndReleasesRoom() throws Exception {
        checkOut(RECEPTIONIST, stayId, "true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayId").value(stayId))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.roomStatus").value("PendingCleaning"))
                .andExpect(jsonPath("$.checkOutDate").value(today.toString()));

        Map<String, Object> stay = jdbcTemplate.queryForMap("SELECT * FROM stay WHERE id = ?::uuid", stayId);
        assertThat(stay.get("check_out_date").toString()).isEqualTo(today.toString());
        assertThat(stay.get("version")).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.PendingCleaning);
        Map<String, Object> history = jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", room.getId());
        assertThat(history)
                .containsEntry("status", "PendingCleaning")
                .containsEntry("previous_status", "Occupied")
                .containsEntry("source_flow", "CHECK_OUT")
                .containsEntry("actor_id", RECEPTIONIST);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT details->>'stayId' FROM room_audit_log WHERE room_id = ? AND event_type = 'CHECK_OUT'",
                        String.class,
                        room.getId()))
                .isEqualTo(stayId);
    }

    @Test
    @DisplayName(
            "T006: encola el CHECK_OUT plano con DEPARTURE y todos los huéspedes, y el CHECK_OUT_BILLING del titular")
    void checkOut_enqueuesBothMessages() throws Exception {
        checkOut(RECEPTIONIST, stayId, "true").andExpect(status().isOk());

        Map<String, Object> departure = jdbcTemplate.queryForMap(
                "SELECT event_type, status, payload->>'movementType' AS movement_type, "
                        + "payload->>'movementDate' AS movement_date, "
                        + "jsonb_array_length(payload->'guests') AS guest_count, "
                        + "payload->'guests'->0->>'birthDate' AS first_birth_date, "
                        + "jsonb_exists(payload->'guests'->0, 'originPlace') AS colombian_has_origin, "
                        + "payload->'guests'->1->>'originPlace' AS foreigner_origin "
                        + "FROM outbox_notification "
                        + "WHERE routing_key = 'habitacion.checkout' AND payload->>'reservationRef' = ?",
                ref);
        assertThat(departure)
                .containsEntry("event_type", "CHECK_OUT")
                .containsEntry("status", "PENDING")
                .containsEntry("movement_type", "DEPARTURE")
                .containsEntry("movement_date", today.toString())
                .containsEntry("guest_count", 2)
                .containsEntry("first_birth_date", "1995-03-20")
                .containsEntry("colombian_has_origin", false)
                .containsEntry("foreigner_origin", "Miami, Estados Unidos");

        Map<String, Object> billing = jdbcTemplate.queryForMap(
                "SELECT event_type, status, payload->>'stayId' AS stay_id, payload->>'roomId' AS room_id, "
                        + "payload->>'categoryRoom' AS category, payload->>'checkInDate' AS check_in, "
                        + "payload->>'checkOutDate' AS check_out, payload->'billingCustomer'->>'name' AS customer, "
                        + "payload->'billingCustomer'->>'taxId' AS tax_id, "
                        + "jsonb_exists(payload, 'guests') AS has_guests "
                        + "FROM outbox_notification "
                        + "WHERE routing_key = 'facturacion.checkout' AND payload->>'reservationRef' = ?",
                ref);
        assertThat(billing)
                .containsEntry("event_type", "CHECK_OUT_BILLING")
                .containsEntry("status", "PENDING")
                .containsEntry("stay_id", stayId)
                .containsEntry("room_id", room.getId().toString())
                .containsEntry("category", "DOBLE")
                .containsEntry("check_in", today.toString())
                .containsEntry("check_out", today.toString())
                .containsEntry("customer", "Ana Pérez")
                .containsEntry("tax_id", "123")
                .containsEntry("has_guests", false);
    }

    @Test
    @DisplayName(
            "T015: sin confirmar responde 400 CONFIRMATION_REQUIRED; sin confirmed o sin X-User, 400; no cambia nada")
    void notConfirmedOrIncomplete_isRejectedWithoutChanges() throws Exception {
        checkOut(RECEPTIONIST, stayId, "false")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CONFIRMATION_REQUIRED"));
        checkOut(RECEPTIONIST, stayId, "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("confirmed"));
        checkOut(null, stayId, "true")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));

        assertNothingChanged();
    }

    @Test
    @DisplayName("T014: una estancia inexistente responde 404 ACTIVE_STAY_NOT_FOUND")
    void unknownStay_isNotFound() throws Exception {
        checkOut(RECEPTIONIST, UUID.randomUUID().toString(), "true")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_STAY_NOT_FOUND"));

        assertNothingChanged();
    }

    @Test
    @DisplayName("T013/T021: un segundo Check-Out de la misma estancia responde 404 y no encola otros mensajes")
    void secondCheckOut_isRejected() throws Exception {
        checkOut(RECEPTIONIST, stayId, "true").andExpect(status().isOk());

        checkOut(RECEPTIONIST, stayId, "true")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_STAY_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_notification WHERE routing_key LIKE '%checkout' "
                                + "AND payload->>'reservationRef' = ?",
                        Integer.class, ref))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("T013: si la habitación ya no está Ocupada responde 409 ROOM_INVALID_STATE con su estado actual")
    void roomNotOccupied_isInvalidState() throws Exception {
        jdbcTemplate.update("UPDATE room SET status = 'InCleaning' WHERE id = ?", room.getId());

        checkOut(RECEPTIONIST, stayId, "true")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("InCleaning"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT check_out_date FROM stay WHERE id = ?::uuid", LocalDate.class, stayId))
                .isNull();
    }

    private void assertNothingChanged() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT check_out_date FROM stay WHERE id = ?::uuid", LocalDate.class, stayId))
                .isNull();
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.Occupied);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_notification WHERE routing_key LIKE '%checkout' "
                                + "AND payload->>'reservationRef' = ?",
                        Integer.class, ref))
                .isZero();
    }

    private ResultActions checkOut(String xUser, String stay, String confirmed) throws Exception {
        var request = post("/api/check-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stayId\":\"" + stay + "\",\"confirmed\":" + confirmed + "}");
        return mockMvc.perform(xUser == null ? request : request.header("X-User", xUser));
    }
}
