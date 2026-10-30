package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageLog;
import com.hospitua.habitaciones.domain.model.DailyReservationMessageType;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/reception/panel/kpis} sobre la aplicación completa y PostgreSQL real, con el reloj real
 * (plan-consultar-panel-recepcion.md T020, T023, T028). La base es compartida, así que cada prueba compara cuánto
 * cambian los KPIs con sus propios datos y no los valores absolutos.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceptionKpisIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StayRepositoryPort stayRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private String roomNumberBase;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        roomNumberBase = "K" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
    }

    @Test
    @DisplayName("T020/T023: cada KPI cuenta sus filas: llegadas pendientes, salidas de hoy, vencidas y ocupadas")
    void kpis_countLocalData() throws Exception {
        givenTodaysListReceived();
        JsonNode before = kpis();

        Room pending = room("1", RoomStatus.Reserved);
        Room arrived = room("2", RoomStatus.Occupied);
        String ref = ref();
        // La habitación 9 no existe en el inventario: no es fila de Llegadas y no cuenta
        DailyReservationRoom unknown = new DailyReservationRoom(UUID.randomUUID(), roomNumberBase + "9", "DOBLE", 1);
        reservationRepository.save(reservation(ref, List.of(roomOf(pending), roomOf(arrived), unknown)));
        stay(ref, arrived, today.plusDays(2), null);
        stay(ref(), room("3", RoomStatus.Occupied), today, null);
        stay(ref(), room("4", RoomStatus.Occupied), today.minusDays(1), null);
        stay(ref(), room("5", RoomStatus.PendingCleaning), today, today);

        JsonNode after = kpis();

        assertThat(delta(before, after, "arrivalsToday")).isEqualTo(1);
        assertThat(delta(before, after, "departuresToday")).isEqualTo(1);
        assertThat(delta(before, after, "overdueDepartures")).isEqualTo(1);
        assertThat(delta(before, after, "occupiedRooms")).isEqualTo(3);
    }

    @Test
    @DisplayName("T028: sin la lista de las 00:00 de hoy, arrivalsToday no viene y los demás KPIs responden")
    void kpis_withoutTodaysList_omitArrivals() throws Exception {
        jdbcTemplate.update(
                "DELETE FROM daily_reservation_message_log WHERE message_type = ? AND operational_date = ?",
                "reserva.lista-del-dia",
                today);

        mockMvc.perform(get("/api/reception/panel/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivalsToday").doesNotExist())
                .andExpect(jsonPath("$.departuresToday").isNumber())
                .andExpect(jsonPath("$.overdueDepartures").isNumber())
                .andExpect(jsonPath("$.occupiedRooms").isNumber());
        mockMvc.perform(get("/api/reception/departures"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays").isArray());
    }

    private JsonNode kpis() throws Exception {
        String body = mockMvc.perform(get("/api/reception/panel/kpis"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JSON.readTree(body);
    }

    private static long delta(JsonNode before, JsonNode after, String field) {
        return after.get(field).asLong() - before.get(field).asLong();
    }

    private void givenTodaysListReceived() {
        reservationRepository.logMessage(new DailyReservationMessageLog(
                UUID.randomUUID().toString(),
                1,
                today,
                DailyReservationMessageType.DAILY_LIST,
                null,
                OffsetDateTime.now(clock)));
    }

    private DailyReservation reservation(String ref, List<DailyReservationRoom> rooms) {
        return new DailyReservation(
                ref,
                "María",
                "González",
                "CC",
                "1032456789",
                "Colombia",
                "BOOKING",
                today,
                today.plusDays(2),
                2,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                rooms);
    }

    private Room room(String suffix, RoomStatus status) {
        Room room = new Room(
                UUID.randomUUID(),
                roomNumberBase + suffix,
                1,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("200000"),
                status,
                null,
                0);
        roomRepository.save(room);
        return room;
    }

    private static DailyReservationRoom roomOf(Room room) {
        return new DailyReservationRoom(
                room.getId(), room.getRoomNumber(), room.getCategoryRoom().name(), 2);
    }

    private void stay(String ref, Room room, LocalDate expectedCheckout, LocalDate checkOut) {
        stayRepository.save(new Stay(
                UUID.randomUUID(),
                ref,
                room.getId(),
                "DIRECTA",
                today.minusDays(3),
                checkOut,
                today.minusDays(3),
                expectedCheckout,
                "Carlos",
                "Pérez",
                "79123456",
                0));
    }

    private static String ref() {
        return "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
