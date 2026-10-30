package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StayRepositoryPort;
import com.hospitua.habitaciones.domain.model.DailyReservation;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/check-in/validate} sobre la aplicación completa y PostgreSQL real, con el reloj real
 * (plan-registrar-check-in.md T008, T017, T019, T020, T026). Solo lee la copia local y el inventario: no hay Módulo 2
 * en la prueba.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CheckInValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private StayRepositoryPort stayRepository;

    @Autowired
    private Clock clock;

    private LocalDate today;
    private String ref;
    private Room room;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        ref = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        room = new Room(
                UUID.randomUUID(),
                "C" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                2,
                RoomCategory.DOBLE,
                3,
                new BigDecimal("200000"),
                RoomStatus.Reserved,
                ref,
                0);
    }

    @Test
    @DisplayName("T008/T017: una reserva de hoy con la habitación Reserved para ella responde 200 con el titular")
    void validate_eligible() throws Exception {
        roomRepository.save(room);
        saveReservation(today);

        validate(ref, room.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationRef").value(ref))
                .andExpect(jsonPath("$.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.categoryRoom").value("DOBLE"))
                .andExpect(jsonPath("$.guestCount").value(2))
                .andExpect(jsonPath("$.maxCapacity").value(3))
                .andExpect(jsonPath("$.startDate").value(today.toString()))
                .andExpect(jsonPath("$.endDate").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.source").value("BOOKING"))
                .andExpect(jsonPath("$.holder.firstName").value("Ana"))
                .andExpect(jsonPath("$.holder.lastName").value("Pérez"))
                .andExpect(jsonPath("$.holder.documentType").value("CC"))
                .andExpect(jsonPath("$.holder.documentNumber").value("123"))
                .andExpect(jsonPath("$.holder.nationality").value("Colombia"));
    }

    @Test
    @DisplayName("Esc. 7: una reserva que no está en la copia local responde 404 RESERVATION_NOT_FOUND")
    void validate_unknownReservation() throws Exception {
        roomRepository.save(room);

        validate(ref, room.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("T026: una reserva que no llega hoy responde 409 INVALID_CHECK_IN_DATE")
    void validate_notArrivingToday() throws Exception {
        roomRepository.save(room);
        saveReservation(today.plusDays(1));

        validate(ref, room.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CHECK_IN_DATE"));
    }

    @Test
    @DisplayName("T020: si el par ya tiene una estancia responde 409 STAY_ALREADY_EXISTS")
    void validate_existingStay() throws Exception {
        roomRepository.save(room);
        saveReservation(today);
        stayRepository.save(new Stay(
                UUID.randomUUID(),
                ref,
                room.getId(),
                "BOOKING",
                today,
                null,
                today,
                today.plusDays(2),
                "Ana",
                "Pérez",
                "123",
                0));

        validate(ref, room.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAY_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("T019: una habitación Available responde 409 ROOM_INVALID_STATE con su estado actual")
    void validate_roomNotReserved() throws Exception {
        roomRepository.save(new Room(
                room.getId(),
                room.getRoomNumber(),
                2,
                RoomCategory.DOBLE,
                3,
                new BigDecimal("200000"),
                RoomStatus.Available,
                null,
                0));
        saveReservation(today);

        validate(ref, room.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ROOM_INVALID_STATE"))
                .andExpect(jsonPath("$.details.currentStatus").value("Available"))
                .andExpect(jsonPath("$.message")
                        .value("La habitación " + room.getRoomNumber() + " está Disponible; solo se puede hacer el"
                                + " Check-In de una habitación Reservada para esta reserva."));
    }

    @Test
    @DisplayName("Sin reservationRef, en blanco o con un roomId que no es UUID responde 400 VALIDATION_ERROR")
    void validate_invalidParameters() throws Exception {
        mockMvc.perform(get("/api/check-in/validate")
                        .param("roomId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.field").value("reservationRef"));
        validate("  ", UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("reservationRef"));
        mockMvc.perform(get("/api/check-in/validate")
                        .param("reservationRef", ref)
                        .param("roomId", "no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("roomId"));
    }

    private ResultActions validate(String reservationRef, UUID roomId) throws Exception {
        return mockMvc.perform(get("/api/check-in/validate")
                .param("reservationRef", reservationRef)
                .param("roomId", roomId.toString()));
    }

    private void saveReservation(LocalDate startDate) {
        reservationRepository.save(new DailyReservation(
                ref,
                "Ana",
                "Pérez",
                "CC",
                "123",
                "Colombia",
                "BOOKING",
                startDate,
                startDate.plusDays(2),
                2,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 2))));
    }
}
