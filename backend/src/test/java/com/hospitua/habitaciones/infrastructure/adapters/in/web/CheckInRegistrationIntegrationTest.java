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
 * {@code POST /api/check-in} sobre la aplicación completa y PostgreSQL real (plan-registrar-check-in.md T010, T013,
 * T014, T020, T021, T022). El worker del Outbox está apagado en el perfil de pruebas, así que la notificación queda pendiente en
 * {@code outbox_notification} y se revisa allí.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckInRegistrationIntegrationTest {

    private static final String RECEPTIONIST = "admin";
    private static final String HOLDER = guestJson("Ana", "CC", "123", "Colombia", null, null, true);
    private static final String COMPANION = guestJson("John", "PAS", "X99", "Perú", "Lima", "Cartagena", false);

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

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        ref = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        room = roomRepository.save(new Room(
                UUID.randomUUID(),
                "K" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
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
    }

    @Test
    @DisplayName("T014: responde 201, crea la estancia y sus huéspedes y deja la habitación Occupied con su historial")
    void register_createsStayGuestsAndOccupiesRoom() throws Exception {
        String stayId = register(RECEPTIONIST, HOLDER, COMPANION)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationRef").value(ref))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.roomStatus").value("Occupied"))
                .andExpect(jsonPath("$.checkInDate").value(today.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"stayId\":\"([^\"]+)\".*", "$1");

        Map<String, Object> stay = jdbcTemplate.queryForMap("SELECT * FROM stay WHERE id = ?::uuid", stayId);
        assertThat(stay)
                .containsEntry("reservation_ref", ref)
                .containsEntry("source", "BOOKING")
                .containsEntry("titular_document_number", "123");
        assertThat(stay.get("check_in_date").toString()).isEqualTo(today.toString());
        assertThat(stay.get("check_out_date")).isNull();

        List<Map<String, Object>> guests = jdbcTemplate.queryForList(
                "SELECT * FROM room_guest WHERE stay_id = ?::uuid ORDER BY document_number", stayId);
        assertThat(guests).hasSize(2);
        assertThat(guests.get(0))
                .containsEntry("document_number", "123")
                .containsEntry("nationality", "Colombia")
                .containsEntry("is_reservation_guest", true);
        assertThat(guests.get(0).get("origin_place")).isNull();
        assertThat(guests.get(1))
                .containsEntry("document_number", "X99")
                .containsEntry("origin_place", "Lima")
                .containsEntry("destination_place", "Cartagena")
                .containsEntry("is_reservation_guest", false);

        Room occupied = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(occupied.getStatus()).isEqualTo(RoomStatus.Occupied);
        assertThat(occupied.getReservedByReservationRef()).isNull();
        Map<String, Object> history = jdbcTemplate.queryForMap(
                "SELECT * FROM room_state_history WHERE room_id = ? AND end_date_time IS NULL", room.getId());
        assertThat(history)
                .containsEntry("status", "Occupied")
                .containsEntry("previous_status", "Reserved")
                .containsEntry("source_flow", "CHECK_IN")
                .containsEntry("actor_id", RECEPTIONIST)
                .containsEntry("reservation_ref", ref);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM room_audit_log WHERE room_id = ? AND event_type = 'CHECK_IN'",
                        Integer.class,
                        room.getId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("T013: encola en la misma transacción el CHECK_IN con ENTRY, la fecha de hoy y los huéspedes")
    void register_enqueuesCheckInMessage() throws Exception {
        register(RECEPTIONIST, HOLDER, COMPANION).andExpect(status().isCreated());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_type, status, message_id, sequence_number, "
                        + "payload->>'movementType' AS movement_type, "
                        + "payload->>'movementDate' AS movement_date, "
                        + "jsonb_array_length(payload->'guests') AS guest_count, "
                        + "jsonb_exists(payload->'guests'->0, 'originPlace') AS colombian_has_origin, "
                        + "payload->'guests'->1->>'originPlace' AS foreigner_origin "
                        + "FROM outbox_notification "
                        + "WHERE routing_key = 'habitacion.checkin' AND payload->>'reservationRef' = ?",
                ref);
        assertThat(row)
                .containsEntry("event_type", "CHECK_IN")
                .containsEntry("status", "PENDING")
                .containsEntry("movement_type", "ENTRY")
                .containsEntry("movement_date", today.toString())
                .containsEntry("guest_count", 2)
                .containsEntry("colombian_has_origin", false)
                .containsEntry("foreigner_origin", "Lima");
        assertThat(row.get("message_id")).isNotNull();
        assertThat(row.get("sequence_number")).isNotNull();
    }

    @Test
    @DisplayName("T021: menos ocupantes que el guestCount responde 400 GUEST_COUNT_MISMATCH y no crea nada")
    void register_guestCountMismatch() throws Exception {
        register(RECEPTIONIST, HOLDER)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GUEST_COUNT_MISMATCH"))
                .andExpect(jsonPath("$.details.expectedGuests").value(2))
                .andExpect(jsonPath("$.details.receivedGuests").value(1));

        assertNothingCreated();
    }

    @Test
    @DisplayName("T021: más ocupantes que la capacidad responde 400 ROOM_CAPACITY_EXCEEDED y no crea nada")
    void register_capacityExceeded() throws Exception {
        register(
                        RECEPTIONIST,
                        HOLDER,
                        COMPANION,
                        guestJson("Luis", "CC", "1", "Colombia", null, null, false),
                        guestJson("Eva", "CC", "2", "Colombia", null, null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ROOM_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.details.maxCapacity").value(3));

        assertNothingCreated();
    }

    @Test
    @DisplayName("T022: un documento repetido responde 400 DUPLICATE_GUEST_DOCUMENT con la posición y no crea nada")
    void register_duplicateDocument() throws Exception {
        register(RECEPTIONIST, HOLDER, guestJson("Ana", "cc", "123", "Colombia", null, null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_GUEST_DOCUMENT"))
                .andExpect(jsonPath("$.details.guestIndex").value(1));

        assertNothingCreated();
    }

    @Test
    @DisplayName("T010: a un extranjero sin lugar de destino se le pide el campo con la posición del huésped")
    void register_foreignerWithoutDestination() throws Exception {
        register(RECEPTIONIST, HOLDER, guestJson("John", "PAS", "X99", "Perú", "Lima", null, false))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("destinationPlace"))
                .andExpect(jsonPath("$.details.guestIndex").value(1));

        assertNothingCreated();
    }

    @Test
    @DisplayName(
            "Sin X-User responde 400 CURRENT_USER_REQUIRED, y sin guests o con un huésped nulo 400 VALIDATION_ERROR;"
                    + " no crea nada")
    void register_missingUserOrGuests() throws Exception {
        register(null, HOLDER, COMPANION)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
        mockMvc.perform(post("/api/check-in")
                        .header("X-User", RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationRef\":\"" + ref + "\",\"roomId\":\"" + room.getId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("guests"));
        register(RECEPTIONIST, HOLDER, "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("guests[1]"));

        assertNothingCreated();
    }

    @Test
    @DisplayName("T020: un segundo Check-In del mismo par responde 409 STAY_ALREADY_EXISTS")
    void register_twice() throws Exception {
        register(RECEPTIONIST, HOLDER, COMPANION).andExpect(status().isCreated());

        register(RECEPTIONIST, HOLDER, COMPANION)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAY_ALREADY_EXISTS"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM stay WHERE reservation_ref = ?", Integer.class, ref))
                .isEqualTo(1);
    }

    private void assertNothingCreated() {
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM stay WHERE reservation_ref = ?", Integer.class, ref))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM outbox_notification WHERE payload->>'reservationRef' = ?",
                        Integer.class,
                        ref))
                .isZero();
        assertThat(roomRepository.findById(room.getId()).orElseThrow().getStatus())
                .isEqualTo(RoomStatus.Reserved);
    }

    private ResultActions register(String xUser, String... guests) throws Exception {
        var request = post("/api/check-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reservationRef\":\"" + ref + "\",\"roomId\":\"" + room.getId() + "\",\"guests\":["
                        + String.join(",", guests) + "]}");
        return mockMvc.perform(xUser == null ? request : request.header("X-User", xUser));
    }

    private static String guestJson(
            String firstName,
            String documentType,
            String documentNumber,
            String nationality,
            String originPlace,
            String destinationPlace,
            boolean reservationGuest) {
        return "{\"firstName\":\"" + firstName + "\",\"lastName\":\"Pérez\",\"documentType\":\"" + documentType
                + "\",\"documentNumber\":\"" + documentNumber + "\",\"nationality\":\"" + nationality
                + "\",\"birthDate\":\"1990-05-20\""
                + (originPlace == null ? "" : ",\"originPlace\":\"" + originPlace + "\"")
                + (destinationPlace == null ? "" : ",\"destinationPlace\":\"" + destinationPlace + "\"")
                + ",\"isReservationGuest\":" + reservationGuest + "}";
    }
}
