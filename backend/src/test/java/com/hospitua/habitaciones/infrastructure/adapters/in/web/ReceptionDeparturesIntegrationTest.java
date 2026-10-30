package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.RoomGuestRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.Stay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/reception/departures} sobre la aplicación completa y PostgreSQL real, con el reloj real
 * (plan-consultar-panel-recepcion.md T014, T015). Solo lee estancias locales: no hay Módulo 2 en la prueba. Cada
 * prueba busca por su propio apellido o referencia, porque la base es compartida.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReceptionDeparturesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StayRepositoryPort stayRepository;

    @Autowired
    private RoomGuestRepositoryPort roomGuestRepository;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private String roomNumberBase;
    private String lastName;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        roomNumberBase = "D" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        lastName = "Apellido" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("T015: estancias activas con salida hoy o vencida, primero las vencidas, con sus huéspedes")
    void departures_activeStaysOverdueFirst() throws Exception {
        Stay dueToday = stay(room("2"), today);
        Stay overdue = stay(room("9"), today.minusDays(2));
        Stay tomorrow = stay(room("3"), today.plusDays(1));
        Stay checkedOut = stay(room("4"), today, today);
        guests(dueToday, 2);
        guests(overdue, 1);

        mockMvc.perform(get("/api/reception/departures").param("query", lastName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays", hasSize(2)))
                .andExpect(jsonPath("$.stays[*].stayId", contains(id(overdue), id(dueToday))))
                .andExpect(jsonPath("$.stays[*].stayId", not(hasItem(id(tomorrow)))))
                .andExpect(jsonPath("$.stays[*].stayId", not(hasItem(id(checkedOut)))))
                .andExpect(jsonPath("$.stays[0].overdue").value(true))
                .andExpect(jsonPath("$.stays[0].guestCount").value(1))
                .andExpect(jsonPath("$.stays[0].expectedCheckoutDate")
                        .value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.stays[1].overdue").value(false))
                .andExpect(jsonPath("$.stays[1].guestCount").value(2))
                .andExpect(jsonPath("$.stays[1].reservationRef").value(dueToday.getReservationRef()))
                .andExpect(
                        jsonPath("$.stays[1].roomId").value(dueToday.getRoomId().toString()))
                .andExpect(jsonPath("$.stays[1].roomNumber").value(roomNumberBase + "2"))
                .andExpect(jsonPath("$.stays[1].categoryRoom").value("SUITE"))
                .andExpect(jsonPath("$.stays[1].titularFirstName").value("Carlos"))
                .andExpect(jsonPath("$.stays[1].titularLastName").value(lastName))
                .andExpect(jsonPath("$.stays[1].titularDocumentNumber").value(dueToday.getTitularDocumentNumber()))
                .andExpect(jsonPath("$.stays[1].checkInDate")
                        .value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.stays[1].source").value("DIRECTA"));
    }

    @Test
    @DisplayName("T014: busca por referencia, documento o habitación exactos y por parte del nombre, sin mayúsculas")
    void departures_byReferenceDocumentRoomOrName() throws Exception {
        Stay stay = stay(room("1"), today);
        String id = id(stay);

        expectFound(stay.getReservationRef().toLowerCase(), id);
        expectFound(" " + stay.getTitularDocumentNumber() + " ", id);
        expectFound((roomNumberBase + "1").toLowerCase(), id);
        expectFound(lastName.substring(3).toUpperCase(), id);
        expectFound("carlos   " + lastName.toLowerCase(), id);

        expectNotFound(stay.getReservationRef().substring(0, 6), id);
        expectNotFound(roomNumberBase, id);
        expectNotFound("%", id);
    }

    @Test
    @DisplayName("Una búsqueda de más de 100 caracteres responde 400 VALIDATION_ERROR")
    void departures_tooLongQuery_isRejected() throws Exception {
        mockMvc.perform(get("/api/reception/departures").param("query", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("query"));
    }

    private void expectFound(String query, String stayId) throws Exception {
        mockMvc.perform(get("/api/reception/departures").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays[*].stayId", hasItem(stayId)));
    }

    private void expectNotFound(String query, String stayId) throws Exception {
        mockMvc.perform(get("/api/reception/departures").param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stays[*].stayId", not(hasItem(stayId))));
    }

    private Room room(String suffix) {
        Room room = new Room(
                UUID.randomUUID(),
                roomNumberBase + suffix,
                2,
                RoomCategory.SUITE,
                4,
                new BigDecimal("300000"),
                RoomStatus.Occupied,
                null,
                0);
        roomRepository.save(room);
        return room;
    }

    private Stay stay(Room room, LocalDate expectedCheckout) {
        return stay(room, expectedCheckout, null);
    }

    private Stay stay(Room room, LocalDate expectedCheckout, LocalDate checkOut) {
        return stayRepository.save(new Stay(
                UUID.randomUUID(),
                "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                room.getId(),
                "DIRECTA",
                today.minusDays(3),
                checkOut,
                today.minusDays(3),
                expectedCheckout,
                "Carlos",
                lastName,
                "DOC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                0));
    }

    private void guests(Stay stay, int count) {
        List<RoomGuest> guests = IntStream.range(0, count)
                .mapToObj(i -> new RoomGuest(
                        UUID.randomUUID(),
                        stay.getId(),
                        "Huésped" + i,
                        lastName,
                        "CC",
                        "G" + i + stay.getTitularDocumentNumber(),
                        "Colombia",
                        LocalDate.of(1990, 1, 1),
                        null,
                        null,
                        i == 0))
                .toList();
        roomGuestRepository.saveAll(guests);
    }

    private static String id(Stay stay) {
        return stay.getId().toString();
    }
}
