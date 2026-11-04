package com.hospitua.habitaciones.application.ports.in;

import java.util.UUID;

/** Confirmar fin de reparación (plan-confirmar-reparacion-finalizada.md T022; FR-004 a FR-010, FR-013, FR-019). */
public interface CompleteRepairUseCase {

    /**
     * Con la habitación bloqueada, la pasa a {@code PendingCleaning}, cierra la tarea con {@code Completed} y, si estaba
     * en {@code TechnicalBlock}, marca su informe como {@code Completed}; publica el aviso a Módulo 2. Solo la confirma su
     * dueño ({@code memberId}).
     *
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotFoundException si la tarea no existe
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotOwnedException si la tarea es de otro miembro
     * @throws com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException si la tarea ya se cerró
     */
    void completeRepair(UUID taskId, String memberId);
}
