package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.ReparationTask;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistencia de las tareas de reparación (plan-confirmar-reparacion-finalizada.md T002; regla 9 del plan base). */
public interface ReparationTaskRepositoryPort {

    /**
     * Guarda la tarea en el momento. Los índices únicos de tareas abiertas son el respaldo de la regla 9.
     *
     * @throws com.hospitua.habitaciones.domain.exception.ActiveTaskExistsException si el miembro ya tiene otra tarea
     *     abierta
     * @throws com.hospitua.habitaciones.domain.exception.RoomTaskExistsException si la habitación ya tiene otra tarea
     *     abierta
     */
    ReparationTask save(ReparationTask task);

    Optional<ReparationTask> findById(UUID taskId);

    /** La habitación de la tarea, para bloquearla antes de cargar la tarea (regla 13 del plan base). */
    Optional<UUID> findRoomIdByTaskId(UUID taskId);

    /** La tarea abierta del miembro ({@code username}), si tiene una. */
    Optional<ReparationTask> findOpenByMember(String memberId);

    /** Si la habitación ya tiene una tarea abierta: "Iniciar reparaciones" lo revisa con la fila bloqueada. */
    boolean existsOpenByRoom(UUID roomId);

    /** Las habitaciones con una tarea abierta, para la etiqueta "En reparación" del panel de mantenimiento. */
    Set<UUID> findRoomIdsWithOpenTask();
}
