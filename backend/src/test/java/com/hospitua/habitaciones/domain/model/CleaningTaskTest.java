package com.hospitua.habitaciones.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException;
import com.hospitua.habitaciones.domain.exception.TaskNotOwnedException;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CleaningTaskTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-09T09:15:00-05:00");

    @Test
    @DisplayName("T002: start crea una tarea abierta del miembro sobre la habitación")
    void start_isOpen() {
        CleaningTask task = CleaningTask.start(ROOM_ID, "jperez", START);

        assertThat(task.getId()).isNotNull();
        assertThat(task.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(task.getCleaningStaffMemberId()).isEqualTo("jperez");
        assertThat(task.getStartDateTime()).isEqualTo(START);
        assertThat(task.isOpen()).isTrue();
        assertThat(task.getEndDateTime()).isNull();
        assertThat(task.getOutcome()).isNull();
    }

    @Test
    @DisplayName("T002: close fija la marca de fin y el resultado; liberar es close(Released, marca)")
    void close_setsEndAndOutcome() {
        CleaningTask task = CleaningTask.start(ROOM_ID, "jperez", START);

        task.close(CleaningTaskOutcome.Released, START.plusMinutes(20));

        assertThat(task.isOpen()).isFalse();
        assertThat(task.getOutcome()).isEqualTo(CleaningTaskOutcome.Released);
        assertThat(task.getEndDateTime()).isEqualTo(START.plusMinutes(20));
    }

    @Test
    @DisplayName("T002: cerrar en la misma marca de inicio se acepta; antes del inicio es IllegalStateException")
    void close_endMustNotPrecedeStart() {
        CleaningTask sameInstant = CleaningTask.start(ROOM_ID, "jperez", START);
        sameInstant.close(CleaningTaskOutcome.Completed, START);
        assertThat(sameInstant.getEndDateTime()).isEqualTo(START);

        CleaningTask task = CleaningTask.start(ROOM_ID, "jperez", START);
        assertThatThrownBy(() -> task.close(CleaningTaskOutcome.Completed, START.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(task.isOpen()).isTrue();
    }

    @Test
    @DisplayName("Una tarea ya cerrada no se vuelve a cerrar")
    void close_twiceIsRejected() {
        CleaningTask task = CleaningTask.start(ROOM_ID, "jperez", START);
        task.close(CleaningTaskOutcome.Completed, START.plusMinutes(30));

        assertThatThrownBy(() -> task.close(CleaningTaskOutcome.Released, START.plusMinutes(40)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(task.getOutcome()).isEqualTo(CleaningTaskOutcome.Completed);
    }

    @Test
    @DisplayName("T002: ensureClosableBy acepta al dueño de una tarea abierta")
    void ensureClosableBy_owner() {
        CleaningTask task = CleaningTask.start(ROOM_ID, "jperez", START);

        assertThatCode(() -> task.ensureClosableBy("jperez")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("T002: ensureClosableBy da TASK_NOT_OWNED antes que TASK_ALREADY_CLOSED")
    void ensureClosableBy_ownershipFirst() {
        CleaningTask task = CleaningTask.start(ROOM_ID, "jperez", START);
        assertThatThrownBy(() -> task.ensureClosableBy("mgomez")).isInstanceOf(TaskNotOwnedException.class);

        task.close(CleaningTaskOutcome.Completed, START.plusMinutes(30));

        assertThatThrownBy(() -> task.ensureClosableBy("mgomez")).isInstanceOf(TaskNotOwnedException.class);
        assertThatThrownBy(() -> task.ensureClosableBy("jperez")).isInstanceOf(TaskAlreadyClosedException.class);
    }

    @Test
    @DisplayName("Una tarea cerrada trae fecha de fin y resultado juntos, como exige el CHECK de la migración")
    void constructor_endAndOutcomeTogether() {
        assertThatThrownBy(() -> new CleaningTask(UUID.randomUUID(), ROOM_ID, "jperez", START, START, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CleaningTask(
                        UUID.randomUUID(), ROOM_ID, "jperez", START, null, CleaningTaskOutcome.Completed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Solo Completed y DamageReported cuentan como limpieza; Released no")
    void countsAsCleaning() {
        assertThat(CleaningTaskOutcome.countsAsCleaning())
                .containsExactlyInAnyOrder(CleaningTaskOutcome.Completed, CleaningTaskOutcome.DamageReported);
    }
}
