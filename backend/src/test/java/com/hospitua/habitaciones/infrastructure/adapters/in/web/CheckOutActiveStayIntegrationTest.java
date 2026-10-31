package com.hospitua.habitaciones.infrastructure.adapters.in.web;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/check-out/active-stay} sobre la aplicación completa y PostgreSQL real (plan-registrar-check-out.md
 * Endpoint 1, T009, T011).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckOutActiveStayIntegrationTest {

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
    private String ref;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        ref = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Test
    @DisplayName(
            "Endpoint 1: estancia activa con la habitación, el titular, la fuente y los huéspedes sin datos migratorios")
    void activeStay_returnsLocalData() throws Exception {
        Room room = givenRoom(RoomStatus.Occupied);
        Stay stay = givenStay(room, null);
        roomGuestRepository.saveAll(List.of(
                guest(stay, "John", "Smith", "PAS", "X99", "Estados Unidos", false),
                guest(stay, "Ana", "Pérez", "CC", "123", "Colombia", true)));

        activeStay(" " + ref + " ", room.getId().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stayId").value(stay.getId().toString()))
                .andExpect(jsonPath("$.reservationRef").value(ref))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.categoryRoom").value("DOBLE"))
                .andExpect(jsonPath("$.roomStatus").value("Occupied"))
                .andExpect(jsonPath("$.titularFirstName").value("Ana"))
                .andExpect(jsonPath("$.titularLastName").value("Pérez"))
                .andExpect(jsonPath("$.checkInDate").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.expectedCheckoutDate").value(today.toString()))
                .andExpect(jsonPath("$.source").value("BOOKING"))
                .andExpect(jsonPath("$.guests.length()").value(2))
                .andExpect(jsonPath("$.guests[0].firstName").value("Ana"))
                .andExpect(jsonPath("$.guests[0].documentType").value("CC"))
                .andExpect(jsonPath("$.guests[1].nationality").value("Estados Unidos"))
                .andExpect(jsonPath("$.guests[1].birthDate").doesNotExist())
                .andExpect(jsonPath("$.guests[1].originPlace").doesNotExist());
    }

    @Test
    @DisplayName("Sin estancia o con la estancia cerrada responde 404 ACTIVE_STAY_NOT_FOUND con el par en details")
    void missingOrClosedStay_isNotFound() throws Exception {
        Room room = givenRoom(RoomStatus.PendingCleaning);
        activeStay(ref, room.getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_STAY_NOT_FOUND"))
                .andExpect(jsonPath("$.details.reservationRef").value(ref))
                .andExpect(jsonPath("$.details.roomId").value(room.getId().toString()));

        givenStay(room, today);
        activeStay(ref, room.getId().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACTIVE_STAY_NOT_FOUND"));
    }

    @Test
    @DisplayName("FR-004: una estancia activa en una habitación que no está Ocupada responde 409 ROOM_INVALID_STATE")
    void roomNotOccupied_isInvalidState() throws Exception {
        Room room = givenRoom(RoomStatus.InCleaning);
        givenStay(room, null);

        activeStay(ref, room.getId().toString())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("InCleaning"));
    }

    @Test
    @DisplayName("Sin reservationRef, con una referencia de más de 100 caracteres o un roomId inválido responde 400")
    void invalidParameters_areValidationErrors() throws Exception {
        String roomId = UUID.randomUUID().toString();
        activeStay("  ", roomId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        activeStay("R".repeat(101), roomId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        activeStay(ref, "no-es-un-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/check-out/active-stay").param("roomId", roomId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    private ResultActions activeStay(String reservationRef, String roomId) throws Exception {
        return mockMvc.perform(get("/api/check-out/active-stay")
                .param("reservationRef", reservationRef)
                .param("roomId", roomId));
    }

    private Room givenRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                "C" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                3,
                new BigDecimal("200000"),
                status,
                null,
                0));
    }

    private Stay givenStay(Room room, LocalDate checkOutDate) {
        return stayRepository.save(new Stay(
                UUID.randomUUID(),
                ref,
                room.getId(),
                "BOOKING",
                today.minusDays(1),
                checkOutDate,
                today.minusDays(1),
                today,
                "Ana",
                "Pérez",
                "123",
                0));
    }

    private static RoomGuest guest(
            Stay stay,
            String firstName,
            String lastName,
            String documentType,
            String documentNumber,
            String nationality,
            boolean reservationGuest) {
        boolean foreigner = !"Colombia".equals(nationality);
        return new RoomGuest(
                UUID.randomUUID(),
                stay.getId(),
                firstName,
                lastName,
                documentType,
                documentNumber,
                nationality,
                LocalDate.of(1990, 5, 12),
                foreigner ? "Miami, Estados Unidos" : null,
                foreigner ? "Cartagena, Colombia" : null,
                reservationGuest);
    }
}
