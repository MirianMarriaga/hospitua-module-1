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

class ReparationTaskTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-09T09:15:00-05:00");

    @Test
    @DisplayName("T002: start crea una tarea abierta del miembro sobre la habitación")
    void start_isOpen() {
        ReparationTask task = ReparationTask.start(ROOM_ID, "mtorres", START);

        assertThat(task.getId()).isNotNull();
        assertThat(task.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(task.getMaintenanceStaffMemberId()).isEqualTo("mtorres");
        assertThat(task.getStartDateTime()).isEqualTo(START);
        assertThat(task.isOpen()).isTrue();
        assertThat(task.getEndDateTime()).isNull();
        assertThat(task.getOutcome()).isNull();
    }

    @Test
    @DisplayName("T002: close fija la marca de fin y el resultado; liberar es close(Released, marca)")
    void close_setsEndAndOutcome() {
        ReparationTask task = ReparationTask.start(ROOM_ID, "mtorres", START);

        task.close(ReparationTaskOutcome.Released, START.plusHours(2));

        assertThat(task.isOpen()).isFalse();
        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Released);
        assertThat(task.getEndDateTime()).isEqualTo(START.plusHours(2));
    }

    @Test
    @DisplayName("T002: cerrar en la misma marca de inicio se acepta; antes del inicio es IllegalStateException")
    void close_endMustNotPrecedeStart() {
        ReparationTask sameInstant = ReparationTask.start(ROOM_ID, "mtorres", START);
        sameInstant.close(ReparationTaskOutcome.Completed, START);
        assertThat(sameInstant.getEndDateTime()).isEqualTo(START);

        ReparationTask task = ReparationTask.start(ROOM_ID, "mtorres", START);
        assertThatThrownBy(() -> task.close(ReparationTaskOutcome.Completed, START.minusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(task.isOpen()).isTrue();
    }

    @Test
    @DisplayName("Una tarea ya cerrada no se vuelve a cerrar")
    void close_twiceIsRejected() {
        ReparationTask task = ReparationTask.start(ROOM_ID, "mtorres", START);
        task.close(ReparationTaskOutcome.Completed, START.plusHours(3));

        assertThatThrownBy(() -> task.close(ReparationTaskOutcome.Released, START.plusHours(4)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(task.getOutcome()).isEqualTo(ReparationTaskOutcome.Completed);
    }

    @Test
    @DisplayName("T002: ensureClosableBy acepta al dueño de una tarea abierta")
    void ensureClosableBy_owner() {
        ReparationTask task = ReparationTask.start(ROOM_ID, "mtorres", START);

        assertThatCode(() -> task.ensureClosableBy("mtorres")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("T002: ensureClosableBy da TASK_NOT_OWNED, con el texto de reparación, antes que TASK_ALREADY_CLOSED")
    void ensureClosableBy_ownershipFirst() {
        ReparationTask task = ReparationTask.start(ROOM_ID, "mtorres", START);
        assertThatThrownBy(() -> task.ensureClosableBy("lperez"))
                .isInstanceOf(TaskNotOwnedException.class)
                .hasMessage("Esta reparación la está haciendo otro compañero.");

        task.close(ReparationTaskOutcome.Completed, START.plusHours(3));

        assertThatThrownBy(() -> task.ensureClosableBy("lperez")).isInstanceOf(TaskNotOwnedException.class);
        assertThatThrownBy(() -> task.ensureClosableBy("mtorres")).isInstanceOf(TaskAlreadyClosedException.class);
    }

    @Test
    @DisplayName("Una tarea cerrada trae fecha de fin y resultado juntos, como exige el CHECK de la migración")
    void constructor_endAndOutcomeTogether() {
        assertThatThrownBy(() -> new ReparationTask(UUID.randomUUID(), ROOM_ID, "mtorres", START, START, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReparationTask(
                        UUID.randomUUID(), ROOM_ID, "mtorres", START, null, ReparationTaskOutcome.Completed))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
