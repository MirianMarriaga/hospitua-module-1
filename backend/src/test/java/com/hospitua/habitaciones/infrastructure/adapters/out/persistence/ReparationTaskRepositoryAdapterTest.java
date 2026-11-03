package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.ReparationTaskRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.RoomRepositoryPort;
import com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException;
import com.hospitua.habitaciones.domain.exception.RoomTaskExistsException;
import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.domain.model.ReparationTaskOutcome;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
 * {@code ReparationTaskRepositoryAdapter} contra PostgreSQL real (plan-confirmar-reparacion-finalizada.md T001, T002).
 * La base es compartida: cada prueba usa habitaciones y miembros propios.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ReparationTaskRepositoryAdapterTest {

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-09T09:15:00-05:00");

    @Autowired
    private ReparationTaskRepositoryPort reparationTaskRepository;

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
        ReparationTask task = reparationTaskRepository.save(ReparationTask.start(roomId, member, START));

        ReparationTask read = reparationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(read.getRoomId()).isEqualTo(roomId);
        assertThat(read.getMaintenanceStaffMemberId()).isEqualTo(member);
        assertThat(read.getStartDateTime()).isAtSameInstantAs(START);
        assertThat(read.isOpen()).isTrue();
        assertThat(reparationTaskRepository.findRoomIdByTaskId(task.getId())).contains(roomId);
        assertThat(reparationTaskRepository.findOpenByMember(member)).contains(task);
        assertThat(reparationTaskRepository.findById(UUID.randomUUID())).isEmpty();
        assertThat(reparationTaskRepository.findRoomIdByTaskId(UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    @DisplayName("T002: guarda el cierre; una tarea cerrada ya no es la abierta del miembro ni de la habitación")
    void close_isPersisted() {
        ReparationTask task = reparationTaskRepository.save(ReparationTask.start(roomId, member, START));
        task.close(ReparationTaskOutcome.Completed, START.plusHours(2));

        reparationTaskRepository.save(task);

        ReparationTask read = reparationTaskRepository.findById(task.getId()).orElseThrow();
        assertThat(read.getOutcome()).isEqualTo(ReparationTaskOutcome.Completed);
        assertThat(read.getEndDateTime()).isAtSameInstantAs(START.plusHours(2));
        assertThat(reparationTaskRepository.findOpenByMember(member)).isEmpty();
        assertThat(reparationTaskRepository.existsOpenByRoom(roomId)).isFalse();
        assertThat(reparationTaskRepository.findRoomIdsWithOpenTask()).doesNotContain(roomId);
    }

    @Test
    @DisplayName("T002: existsOpenByRoom y findRoomIdsWithOpenTask solo cuentan las tareas abiertas")
    void openTasksByRoom() {
        UUID closedRoom = newRoom();
        UUID idleRoom = newRoom();
        reparationTaskRepository.save(ReparationTask.start(roomId, member, START));
        ReparationTask released = reparationTaskRepository.save(ReparationTask.start(closedRoom, newMember(), START));
        released.close(ReparationTaskOutcome.Released, START.plusMinutes(10));
        reparationTaskRepository.save(released);

        assertThat(reparationTaskRepository.existsOpenByRoom(roomId)).isTrue();
        assertThat(reparationTaskRepository.existsOpenByRoom(closedRoom)).isFalse();
        assertThat(reparationTaskRepository.existsOpenByRoom(idleRoom)).isFalse();
        assertThat(reparationTaskRepository.findRoomIdsWithOpenTask())
                .contains(roomId)
                .doesNotContain(closedRoom, idleRoom);
    }

    @Test
    @DisplayName("T001/T002: una segunda tarea abierta del miembro es ACTIVE_TASK_EXISTS")
    void secondOpenTaskOfMember_isActiveTaskExists() {
        reparationTaskRepository.save(ReparationTask.start(roomId, member, START));

        assertThatThrownBy(() -> reparationTaskRepository.save(ReparationTask.start(newRoom(), member, START)))
                .isInstanceOf(ActiveTaskExistsException.class);
    }

    @Test
    @DisplayName("T001/T002: una segunda tarea abierta de la habitación es ROOM_TASK_EXISTS")
    void secondOpenTaskOfRoom_isRoomTaskExists() {
        reparationTaskRepository.save(ReparationTask.start(roomId, member, START));

        assertThatThrownBy(() -> reparationTaskRepository.save(ReparationTask.start(roomId, newMember(), START)))
                .isInstanceOf(RoomTaskExistsException.class);
    }

    @Test
    @DisplayName("T001: las tareas cerradas no impiden abrir otra del mismo miembro y la misma habitación")
    void closedTasks_doNotBlock() {
        ReparationTask first = reparationTaskRepository.save(ReparationTask.start(roomId, member, START));
        first.close(ReparationTaskOutcome.Released, START.plusMinutes(5));
        reparationTaskRepository.save(first);

        ReparationTask second =
                reparationTaskRepository.save(ReparationTask.start(roomId, member, START.plusMinutes(10)));

        assertThat(reparationTaskRepository.findOpenByMember(member)).contains(second);
    }

    private UUID newRoom() {
        return roomRepository
                .save(new Room(
                        UUID.randomUUID(),
                        "R" + ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000),
                        1,
                        RoomCategory.SENCILLA,
                        2,
                        new BigDecimal("150000"),
                        RoomStatus.DisabledForRepairs,
                        null,
                        0))
                .getId();
    }

    private static String newMember() {
        return "mant-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
