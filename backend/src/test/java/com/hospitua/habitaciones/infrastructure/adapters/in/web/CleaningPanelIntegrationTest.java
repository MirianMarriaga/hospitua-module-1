package com.hospitua.habitaciones.infrastructure.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.DailyReservationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.StaffAccountRepositoryPort;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.DailyReservation;
import com.hospitua.habitaciones.domain.model.DailyReservationRoom;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/cleaning/panel} sobre la aplicación completa y PostgreSQL real (plan-consultar-panel-limpieza.md
 * T008, T012). La base es compartida: el panel lista todas las habitaciones en {@code PendingCleaning} y
 * {@code Available}, así que cada prueba revisa solo las que crea.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CleaningPanelIntegrationTest {

    private static final String RECEPTIONIST = "admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomRepositoryPort roomRepository;

    @Autowired
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Autowired
    private StaffAccountRepositoryPort staffAccountRepository;

    @Autowired
    private DailyReservationRepositoryPort reservationRepository;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("T008: lista PendingCleaning y Available con su última limpieza, sin Released ni otros estados")
    void panel_listsRoomsWithLastCleaning() throws Exception {
        UUID available = newRoom(RoomStatus.Available).getId();
        UUID pending = newRoom(RoomStatus.PendingCleaning).getId();
        UUID inCleaning = newRoom(RoomStatus.InCleaning).getId();
        UUID occupied = newRoom(RoomStatus.Occupied).getId();
        OffsetDateTime cleaningEnd = OffsetDateTime.parse("2026-10-07T16:40:00-05:00");
        closedTask(pending, cleaningEnd.minusHours(1), cleaningEnd, CleaningTaskOutcome.Completed);
        closedTask(pending, cleaningEnd.plusHours(1), cleaningEnd.plusHours(2), CleaningTaskOutcome.Released);

        String body = mockMvc.perform(get("/api/cleaning/panel").header("X-User", newCleaningMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTask").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> roomIds = JsonPath.read(body, "$.rooms[*].roomId");
        assertThat(roomIds)
                .contains(available.toString(), pending.toString())
                .doesNotContain(inCleaning.toString(), occupied.toString());
        assertThat(roomIds.indexOf(pending.toString())).isLessThan(roomIds.indexOf(available.toString()));
        List<String> lastCleaning = JsonPath.read(body, roomPath(pending) + ".lastCleaningDateTime");
        assertThat(lastCleaning).hasSize(1);
        assertThat(lastCleaning.getFirst()).endsWith("-05:00");
        assertThat(OffsetDateTime.parse(lastCleaning.getFirst())).isAtSameInstantAs(cleaningEnd);
        List<String> pendingStatus = JsonPath.read(body, roomPath(pending) + ".status");
        assertThat(pendingStatus).containsExactly("PendingCleaning");
        List<String> neverCleaned = JsonPath.read(body, roomPath(available) + ".lastCleaningDateTime");
        assertThat(neverCleaned).isEmpty();
    }

    @Test
    @DisplayName("T008: con tarea abierta responde activeTask con la fila de su habitación y rooms vacío")
    void panel_activeTask() throws Exception {
        String member = newCleaningMember();
        Room room = newRoom(RoomStatus.PendingCleaning);
        mockMvc.perform(post("/api/cleaning/tasks")
                        .header("X-User", member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":\"" + room.getId() + "\"}"))
                .andExpect(status().isCreated());
        CleaningTask task = cleaningTaskRepository.findOpenByMember(member).orElseThrow();

        mockMvc.perform(get("/api/cleaning/panel").header("X-User", member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeTask.taskId").value(task.getId().toString()))
                .andExpect(jsonPath("$.activeTask.startDateTime").isNotEmpty())
                .andExpect(
                        jsonPath("$.activeTask.room.roomId").value(room.getId().toString()))
                .andExpect(jsonPath("$.activeTask.room.roomNumber").value(room.getRoomNumber()))
                .andExpect(jsonPath("$.activeTask.room.categoryRoom").value("SENCILLA"))
                .andExpect(jsonPath("$.activeTask.room.status").value("InCleaning"))
                .andExpect(jsonPath("$.rooms").isEmpty());
    }

    @Test
    @DisplayName("Sin X-User responde 400 CURRENT_USER_REQUIRED")
    void panel_withoutCurrentUser() throws Exception {
        mockMvc.perform(get("/api/cleaning/panel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CURRENT_USER_REQUIRED"));
    }

    @Test
    @DisplayName("T012: una habitación que sale por check-out aparece PendingCleaning en la siguiente consulta")
    void panel_showsRoomAfterCheckOut() throws Exception {
        String member = newCleaningMember();
        Room room = checkedInRoom();
        assertThat(roomIds(member)).doesNotContain(room.getId().toString());
        String stayId = activeStayId(room);

        mockMvc.perform(post("/api/check-out")
                        .header("X-User", RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stayId\":\"" + stayId + "\",\"confirmed\":true}"))
                .andExpect(status().isOk());

        String body = panel(member);
        List<String> status = JsonPath.read(body, roomPath(room.getId()) + ".status");
        assertThat(status).containsExactly("PendingCleaning");
    }

    /** Habitación ocupada por un Check-In real ({@code POST /api/check-in}) de una reserva de hoy. */
    private Room checkedInRoom() throws Exception {
        LocalDate today = LocalDate.now(clock);
        String ref = "RSV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Room room = roomRepository.save(new Room(
                UUID.randomUUID(),
                roomNumber(),
                2,
                RoomCategory.DOBLE,
                2,
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
                today.plusDays(1),
                1,
                OffsetDateTime.parse("2026-10-08T15:42:10-05:00"),
                List.of(new DailyReservationRoom(room.getId(), room.getRoomNumber(), "DOBLE", 1))));
        mockMvc.perform(post("/api/check-in")
                        .header("X-User", RECEPTIONIST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservationRef\":\"" + ref + "\",\"roomId\":\"" + room.getId() + "\",\"guests\":["
                                + "{\"firstName\":\"Ana\",\"lastName\":\"Pérez\",\"documentType\":\"CC\","
                                + "\"documentNumber\":\"123\",\"nationality\":\"Colombia\",\"birthDate\":\"1995-03-20\","
                                + "\"isReservationGuest\":true}]}"))
                .andExpect(status().isCreated());
        return room;
    }

    private String activeStayId(Room room) throws Exception {
        String ref = room.getReservedByReservationRef();
        return JsonPath.read(
                mockMvc.perform(get("/api/check-out/active-stay")
                                .header("X-User", RECEPTIONIST)
                                .param("reservationRef", ref)
                                .param("roomId", room.getId().toString()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString(),
                "$.stayId");
    }

    private List<String> roomIds(String member) throws Exception {
        return JsonPath.read(panel(member), "$.rooms[*].roomId");
    }

    private String panel(String member) throws Exception {
        return mockMvc.perform(get("/api/cleaning/panel").header("X-User", member))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static String roomPath(UUID roomId) {
        return "$.rooms[?(@.roomId == '" + roomId + "')]";
    }

    private void closedTask(UUID roomId, OffsetDateTime start, OffsetDateTime end, CleaningTaskOutcome outcome) {
        cleaningTaskRepository.save(new CleaningTask(UUID.randomUUID(), roomId, newMember(), start, end, outcome));
    }

    private Room newRoom(RoomStatus status) {
        return roomRepository.save(new Room(
                UUID.randomUUID(),
                roomNumber(),
                2,
                RoomCategory.SENCILLA,
                2,
                new BigDecimal("150000"),
                status,
                null,
                0));
    }

    private String newCleaningMember() {
        String username = newMember();
        staffAccountRepository.save(StaffAccount.createNew(
                username, "$2a$10$hash", "Personal de limpieza", StaffArea.LIMPIEZA, OffsetDateTime.now(), "admin"));
        return username;
    }

    private static String roomNumber() {
        return "P" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000);
    }

    private static String newMember() {
        return "panel" + UUID.randomUUID().toString().substring(0, 8);
    }
}
