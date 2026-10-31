package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.CleaningTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@code CleaningTaskRepositoryAdapter} contra PostgreSQL real (plan-marcar-habitacion-en-limpieza.md T001, T002). La
 * base es compartida: cada prueba usa habitaciones y miembros propios.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class CleaningTaskRepositoryAdapterTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-09T09:15:00-05:00");

    @Autowired
    private CleaningTaskRepositoryPort cleaningTaskRepository;

    @Autowired
    private RoomRepositoryPort roomRepository;

    private UUID roomId;
    private String member;

    @BeforeEach
    void setUp() {
        roomId = newRoom();
        member = newMember();
    }

    @Test
    @DisplayName("T002: guarda y lee la tarea, su habitación y la tarea abierta del miembro")
    void saveAndRead() {
        CleaningTask task = cleaningTaskRepository.save(CleaningTask.start(roomId, member, START));

        CleaningTask read = cleaningTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(read.getRoomId()).isEqualTo(roomId);
        assertThat(read.getCleaningStaffMemberId()).isEqualTo(member);
        assertThat(read.getStartDateTime()).isAtSameInstantAs(START);
        assertThat(read.isOpen()).isTrue();
        assertThat(cleaningTaskRepository.findRoomIdByTaskId(task.getId())).contains(roomId);
        assertThat(cleaningTaskRepository.findOpenByMember(member)).contains(task);
        assertThat(cleaningTaskRepository.findById(UUID.randomUUID())).isEmpty();
        assertThat(cleaningTaskRepository.findRoomIdByTaskId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("T002: guarda el cierre; una tarea cerrada ya no es la abierta del miembro")
    void close_isPersisted() {
        CleaningTask task = cleaningTaskRepository.save(CleaningTask.start(roomId, member, START));
        task.close(CleaningTaskOutcome.DamageReported, START.plusMinutes(25));

        cleaningTaskRepository.save(task);

        CleaningTask read = cleaningTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(read.getOutcome()).isEqualTo(CleaningTaskOutcome.DamageReported);
        assertThat(read.getEndDateTime()).isAtSameInstantAs(START.plusMinutes(25));
        assertThat(cleaningTaskRepository.findOpenByMember(member)).isEmpty();
    }

    @Test
    @DisplayName("T001/T002: una segunda tarea abierta del miembro es ACTIVE_TASK_EXISTS")
    void secondOpenTaskOfMember_isActiveTaskExists() {
        cleaningTaskRepository.save(CleaningTask.start(roomId, member, START));

        assertThatThrownBy(() -> cleaningTaskRepository.save(CleaningTask.start(newRoom(), member, START)))
                .isInstanceOf(ActiveTaskExistsException.class);
    }

    @Test
    @DisplayName("T001/T002: una segunda tarea abierta de la habitación es ROOM_TASK_EXISTS")
    void secondOpenTaskOfRoom_isRoomTaskExists() {
        cleaningTaskRepository.save(CleaningTask.start(roomId, member, START));

        assertThatThrownBy(() -> cleaningTaskRepository.save(CleaningTask.start(roomId, newMember(), START)))
                .isInstanceOf(RoomTaskExistsException.class);
    }

    @Test
    @DisplayName("T001: las tareas cerradas no impiden abrir otra del mismo miembro y la misma habitación")
    void closedTasks_doNotBlock() {
        CleaningTask first = cleaningTaskRepository.save(CleaningTask.start(roomId, member, START));
        first.close(CleaningTaskOutcome.Released, START.plusMinutes(5));
        cleaningTaskRepository.save(first);

        CleaningTask second = cleaningTaskRepository.save(CleaningTask.start(roomId, member, START.plusMinutes(10)));

        assertThat(cleaningTaskRepository.findOpenByMember(member)).contains(second);
    }

    @Test
    @DisplayName("T002: la última limpieza por habitación en una consulta: solo cuentan Completed y DamageReported")
    void lastCleaningEnd_byRoom() {
        UUID otherRoom = newRoom();
        UUID neverCleaned = newRoom();
        closedTask(roomId, START, CleaningTaskOutcome.Completed);
        closedTask(roomId, START.plusHours(2), CleaningTaskOutcome.DamageReported);
        closedTask(roomId, START.plusHours(4), CleaningTaskOutcome.Released);
        closedTask(otherRoom, START.plusHours(1), CleaningTaskOutcome.Completed);
        closedTask(neverCleaned, START.plusHours(3), CleaningTaskOutcome.Released);
        cleaningTaskRepository.save(CleaningTask.start(otherRoom, newMember(), START.plusHours(5)));

        Map<UUID, OffsetDateTime> lastEnds = cleaningTaskRepository.findLastCleaningEndByRoomIds(
                List.of(roomId, otherRoom, neverCleaned), CleaningTaskOutcome.countsAsCleaning());

        assertThat(lastEnds).containsOnlyKeys(roomId, otherRoom);
        assertThat(lastEnds.get(roomId)).isAtSameInstantAs(START.plusHours(2).plusMinutes(30));
        assertThat(lastEnds.get(otherRoom)).isAtSameInstantAs(START.plusHours(1).plusMinutes(30));
    }

    @Test
    @DisplayName("Sin habitaciones o sin resultados contados no consulta y devuelve vacío")
    void lastCleaningEnd_emptyInput() {
        assertThat(cleaningTaskRepository.findLastCleaningEndByRoomIds(
                        List.of(), CleaningTaskOutcome.countsAsCleaning()))
                .isEmpty();
        assertThat(cleaningTaskRepository.findLastCleaningEndByRoomIds(List.of(roomId), Set.of()))
                .isEmpty();
    }

    /** Tarea de 30 minutos ya cerrada; cada una con su propio miembro. */
    private void closedTask(UUID room, OffsetDateTime start, CleaningTaskOutcome outcome) {
        CleaningTask task = cleaningTaskRepository.save(CleaningTask.start(room, newMember(), start));
        task.close(outcome, start.plusMinutes(30));
        cleaningTaskRepository.save(task);
    }

    private UUID newRoom() {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "L" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        1,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("150000"),
                        RoomStatus.InCleaning,
                        null,
                        0))
                .getId();
    }

    private static String newMember() {
        return "limp-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
