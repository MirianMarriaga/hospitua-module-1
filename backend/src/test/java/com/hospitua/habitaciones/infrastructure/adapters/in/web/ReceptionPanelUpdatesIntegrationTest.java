package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import com.hospitua.habitaciones.application.dto.DailyReservationUpdateMessageDto;
import com.hospitua.habitaciones.application.ports.in.IngestDailyReservationsUseCase;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationIngestionOutcome;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.DailyReservationUpdateType;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tras procesar la lista del día y un {@code ADDED}, un {@code UPDATED} y un {@code REMOVED}, la siguiente consulta de
 * {@code arrivals} y {@code panel/kpis} refleja cada cambio (plan-consultar-panel-recepcion.md T029; FR-006). Las
 * habitaciones que la actualización deja de apartar aparecen enseguida en el inventario de {@code Available}
 * (plan-marcar-habitacion-como-disponible.md T020).
 *
 * <p>Usa su propio contexto y su propia base: la ingesta descarta los mensajes de un día anterior al de la copia local,
 * y las pruebas de la cola dejan en la base compartida listas de días futuros que harían descartar las de hoy.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.application.name=hospitua-panel-recepcion-actualizaciones")
class ReceptionPanelUpdatesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestDailyReservationsUseCase ingestion;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("T029: ADDED, UPDATED y REMOVED cambian Llegadas y arrivalsToday en la siguiente consulta")
    void updates_areReflectedInArrivalsAndKpis() throws Exception {
        LocalDate today = LocalDate.now(clock);
        String base = "U" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
        Room a1 = availableRoom(base + "1");
        Room b1 = availableRoom(base + "2");
        Room b2 = availableRoom(base + "3");
        String refA = "RSV-A-" + base;
        String refB = "RSV-B-" + base;

        applied(ingestion.ingestDailyList(new DailyReservationListMessageDto(
                UUID.randomUUID().toString(), 1, today, List.of(reservation(refA, today, a1)))));
        assertArrivals(1, refA);
        assertReservedBy(a1, refA);

        applied(ingestion.ingestUpdate(
                update(2, today, DailyReservationUpdateType.ADDED, reservation(refB, today, b1, b2))));
        assertArrivals(3, refA, refB);
        assertReservedBy(b1, refB);
        assertReservedBy(b2, refB);

        applied(ingestion.ingestUpdate(
                update(3, today, DailyReservationUpdateType.UPDATED, reservation(refB, today, b1))));
        assertArrivals(2, refA, refB);
        mockMvc.perform(get("/api/reception/arrivals").param("query", refB))
                .andExpect(jsonPath("$.groups[0].totalRooms").value(1))
                .andExpect(jsonPath("$.groups[0].rooms[*].roomId")
                        .value(contains(b1.getId().toString())));
        assertAvailableInInventory(b2);

        applied(ingestion.ingestUpdate(new DailyReservationUpdateMessageDto(
                UUID.randomUUID().toString(), 4, today, DailyReservationUpdateType.REMOVED, refA, null)));
        assertArrivals(1, refB);
        assertAvailableInInventory(a1);
    }

    /** Llegadas lista exactamente esas reservas y {@code arrivalsToday} cuenta sus habitaciones pendientes. */
    private void assertArrivals(int pendingRooms, String... refs) throws Exception {
        mockMvc.perform(get("/api/reception/arrivals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listAvailable").value(true))
                .andExpect(jsonPath("$.groups", hasSize(refs.length)))
                .andExpect(jsonPath("$.groups[*].reservationRef").value(containsInAnyOrder((Object[]) refs)));
        mockMvc.perform(get("/api/reception/panel/kpis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivalsToday").value(pendingRooms));
    }

    private void assertReservedBy(Room room, String ref) {
        Room current = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(current.getStatus()).isEqualTo(RoomStatus.Reserved);
        assertThat(current.getReservedByReservationRef()).isEqualTo(ref);
    }

    private void assertAvailableInInventory(Room room) throws Exception {
        mockMvc.perform(get("/api/rooms/inventory")
                        .param("roomNumber", room.getRoomNumber())
                        .param("status", "Available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].roomId").value(room.getId().toString()));
    }

    private static void applied(DailyReservationIngestionOutcome outcome) {
        assertThat(outcome).isEqualTo(DailyReservationIngestionOutcome.APPLIED);
    }

    private static DailyReservationUpdateMessageDto update(
            long sequence, LocalDate today, DailyReservationUpdateType type, DailyReservation reservation) {
        return new DailyReservationUpdateMessageDto(
                UUID.randomUUID().toString(), sequence, today, type, reservation.reservationRef(), reservation);
    }

    private DailyReservation reservation(String ref, LocalDate today, Room... rooms) {
        List<DailyReservationRoom> reservationRooms = Arrays.stream(rooms)
                .map(room -> new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 1))
                .toList();
        return new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123",
                "Colombia",
                "DIRECTA",
                today,
                today.plusDays(2),
                rooms.length,
                OffsetDateTime.now(clock),
                reservationRooms);
    }

    private Room availableRoom(String number) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                number,
                2,
                RoomCategory.DOBLE,
                2,
                new BigDecimal("150000"),
                RoomStatus.Available,
                null,
                0));
    }
}
