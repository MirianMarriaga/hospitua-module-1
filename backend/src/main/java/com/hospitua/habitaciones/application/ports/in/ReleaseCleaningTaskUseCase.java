package com.hospitua.habitaciones.application.ports.in;

import java.util.UUID;

/** Liberar tarea desde la vista de tarea activa (plan-confirmar-fin-de-limpieza.md T017; FR-013, FR-016). */
public interface ReleaseCleaningTaskUseCase {

    /**
     * Con la habitación bloqueada, la devuelve de {@code InCleaning} a {@code PendingCleaning} y cierra la tarea con
     * {@code Released}. Solo la libera su dueño ({@code memberId}).
     *
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotFoundException si la tarea no existe
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotOwnedException si la tarea es de otro miembro
     * @throws com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException si la tarea ya se cerró
     */
    void releaseTask(UUID taskId, String memberId);
}
