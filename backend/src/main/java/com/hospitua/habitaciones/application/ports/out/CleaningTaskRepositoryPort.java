package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistencia de las tareas de limpieza (plan-marcar-habitacion-en-limpieza.md T002; regla 9 del plan base). */
public interface CleaningTaskRepositoryPort {

    /**
     * Guarda la tarea en el momento. Los índices únicos de tareas abiertas son el respaldo de la regla 9.
     *
     * @throws com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException si el miembro ya tiene otra tarea
     *     abierta
     * @throws com.hospitua.habitaciones.domain.exception.RoomTaskExistsException si la habitación ya tiene otra tarea
     *     abierta
     */
    CleaningTask save(CleaningTask task);

    Optional<CleaningTask> findById(UUID taskId);

    /** La habitación de la tarea, para bloquearla antes de cargar la tarea (regla 13 del plan base). */
    Optional<UUID> findRoomIdByTaskId(UUID taskId);

    /** La tarea abierta del miembro ({@code username}), si tiene una. */
    Optional<CleaningTask> findOpenByMember(String memberId);

    /**
     * Fin de la última limpieza de cada habitación, en una sola consulta; las habitaciones sin limpiezas no aparecen.
     * {@code countedOutcomes} son los de {@link CleaningTaskOutcome#countsAsCleaning()}.
     */
    Map<UUID, OffsetDateTime> findLastCleaningEndByRoomIds(
            Collection<UUID> roomIds, Set<CleaningTaskOutcome> countedOutcomes);
}
