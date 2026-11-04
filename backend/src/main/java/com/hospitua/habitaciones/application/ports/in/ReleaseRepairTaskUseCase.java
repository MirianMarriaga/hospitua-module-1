package com.hospitua.habitaciones.application.ports.in;

import java.util.UUID;

/** Liberar tarea desde la vista de tarea activa (plan-confirmar-reparacion-finalizada.md T023; FR-014, FR-016). */
public interface ReleaseRepairTaskUseCase {

    /**
     * Con la habitación bloqueada, cierra la tarea con {@code Released} sin cambiar la habitación ni su informe. Solo la
     * libera su dueño ({@code memberId}).
     *
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotFoundException si la tarea no existe
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotOwnedException si la tarea es de otro miembro
     * @throws com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException si la tarea ya se cerró
     */
    void releaseTask(UUID taskId, String memberId);
}
