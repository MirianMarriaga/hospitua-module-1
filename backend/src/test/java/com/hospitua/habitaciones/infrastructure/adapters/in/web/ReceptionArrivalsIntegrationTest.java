package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
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

/**
 * {@code GET /api/reception/arrivals} sobre la aplicación completa y PostgreSQL real, con el reloj real
 * (plan-consultar-panel-recepcion.md T009, T028; plan-consultar-reservas.md T017). La consulta sale solo de la copia
 * local: no hay Módulo 2 en la prueba, así que una llamada REST haría fallar la petición. Cada prueba busca por su
 * propia referencia o apellido, porque la base es compartida.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceptionArrivalsIntegrationTest {

    private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse("2026-10-08T15:42:10-05:00");
    private static final String DAILY_LIST = "reserva.lista-del-dia";

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
        roomNumberBase = "R" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
    }

    @Test
    @DisplayName("T009: una fila por habitación pendiente, con alerta en bloqueo técnico e indicador en limpieza")
    void arrivals_rowsWithNoticesFromRoomStatus() throws Exception {
        givenTodaysListReceived();
        Room reserved = room("1", RoomStatus.Reserved);
        Room blocked = room("2", RoomStatus.TechnicalBlock);
        Room cleaning = room("3", RoomStatus.InCleaning);
        Room checkedIn = room("4", RoomStatus.Occupied);
        String ref = ref();
        reservationRepository.save(reservation(ref, "González", reserved, blocked, cleaning, checkedIn));
        checkIn(ref, checkedIn);

        mockMvc.perform(get("/api/reception/arrivals").param("query", "  " + ref.toLowerCase() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listAvailable").value(true))
                .andExpect(jsonPath("$.groups", hasSize(1)))
                .andExpect(jsonPath("$.groups[0].reservationRef").value(ref))
                .andExpect(jsonPath("$.groups[0].pendingRooms").value(3))
                .andExpect(jsonPath("$.groups[0].totalRooms").value(4))
                .andExpect(jsonPath("$.groups[0].rooms", hasSize(3)))
                .andExpect(jsonPath("$.groups[0].rooms[0].roomId")
                        .value(reserved.getId().toString()))
                .andExpect(jsonPath("$.groups[0].rooms[0].roomNumber").value(reserved.getRoomNumber()))
                .andExpect(jsonPath("$.groups[0].rooms[0].categoryRoom").value("DOBLE"))
                .andExpect(jsonPath("$.groups[0].rooms[0].titularFirstName").value("María"))
                .andExpect(jsonPath("$.groups[0].rooms[0].titularLastName").value("González"))
                .andExpect(
                        jsonPath("$.groups[0].rooms[0].titularDocumentNumber").value("1032456789"))
                .andExpect(jsonPath("$.groups[0].rooms[0].startDate").value(today.toString()))
                .andExpect(jsonPath("$.groups[0].rooms[0].endDate")
                        .value(today.plusDays(3).toString()))
                .andExpect(jsonPath("$.groups[0].rooms[0].nights").value(3))
                .andExpect(jsonPath("$.groups[0].rooms[0].guestCount").value(2))
                .andExpect(jsonPath("$.groups[0].rooms[0].source").value("BOOKING"))
                .andExpect(jsonPath("$.groups[0].rooms[0].roomStatus").value("Reserved"))
                .andExpect(jsonPath("$.groups[0].rooms[0].notice").doesNotExist())
                .andExpect(jsonPath("$.groups[0].rooms[1].roomStatus").value("TechnicalBlock"))
                .andExpect(jsonPath("$.groups[0].rooms[1].notice.type").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.groups[0].rooms[1].notice.text").value("No disponible: Bloqueo técnico"))
                .andExpect(jsonPath("$.groups[0].rooms[2].roomStatus").value("InCleaning"))
                .andExpect(jsonPath("$.groups[0].rooms[2].notice.type").value("PENDING_ASSIGNMENT"))
                .andExpect(jsonPath("$.groups[0].rooms[2].notice.text")
                        .value("En limpieza: se apartará al terminar la limpieza"));
    }

    @Test
    @DisplayName("T017: varias reservas que coinciden se devuelven todas; sin coincidencias, groups vacío")
    void arrivals_multipleMatchesAndNoMatches() throws Exception {
        givenTodaysListReceived();
        String lastName = "Apellido" + UUID.randomUUID().toString().substring(0, 8);
        String first = ref();
        String second = ref();
        reservationRepository.save(reservation(first, lastName, room("1", RoomStatus.Reserved)));
        reservationRepository.save(reservation(second, lastName, room("2", RoomStatus.Reserved)));

        mockMvc.perform(get("/api/reception/arrivals").param("query", lastName.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[*].reservationRef", containsInAnyOrder(first, second)));
        mockMvc.perform(get("/api/reception/arrivals").param("query", "sin-coincidencias-" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listAvailable").value(true))
                .andExpect(jsonPath("$.groups", hasSize(0)));
    }

    @Test
    @DisplayName("T028: sin la lista de las 00:00 de hoy responde listAvailable = false y groups vacío")
    void arrivals_withoutTodaysList_isNotAvailable() throws Exception {
        jdbcTemplate.update(
                "DELETE FROM daily_reservation_message_log WHERE message_type = ? AND operational_date = ?",
                DAILY_LIST,
                today);
        String ref = ref();
        reservationRepository.save(reservation(ref, "González", room("1", RoomStatus.Reserved)));

        mockMvc.perform(get("/api/reception/arrivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listAvailable").value(false))
                .andExpect(jsonPath("$.groups", hasSize(0)));
    }

    @Test
    @DisplayName("Una búsqueda de más de 100 caracteres responde 400 VALIDATION_ERROR sin consultar")
    void arrivals_tooLongQuery_isRejected() throws Exception {
        mockMvc.perform(get("/api/reception/arrivals").param("query", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("query"));
        mockMvc.perform(get("/api/reception/arrivals").param("query", "a".repeat(100)))
                .andExpect(status().isOk());
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

    private DailyReservation reservation(String ref, String lastName, Room... rooms) {
        List<DailyReservationRoom> reservationRooms = List.of(rooms).stream()
                .map(room -> new DailyReservationRoom(
                        room.getId(),
                        room.getRoomNumber(),
                        room.getCategoryRoom().name(),
                        2))
                .toList();
        return new DailyReservation(
                ref,
                "María",
                lastName,
                "CC",
                "1032456789",
                "Colombia",
                "BOOKING",
                today,
                today.plusDays(3),
                2,
                UPDATED_AT,
                reservationRooms);
    }

    private Room room(String suffix, RoomStatus status) {
        Room room = new Room(
                UUID.randomUUID(),
                roomNumberBase + suffix,
                3,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("200000"),
                status,
                null,
                0);
        roomRepository.save(room);
        return room;
    }

    private void checkIn(String ref, Room room) {
        stayRepository.save(new Stay(
                UUID.randomUUID(),
                ref,
                room.getId(),
                "BOOKING",
                today,
                null,
                today,
                today.plusDays(3),
                "María",
                "González",
                "1032456789",
                0));
    }

    private static String ref() {
        return "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
