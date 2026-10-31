package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.CompleteCleaningCommand;

/** Confirmar fin de limpieza (plan-confirmar-fin-de-limpieza.md T016). */
public interface CompleteCleaningUseCase {

    /**
     * En una sola transacción, con la habitación bloqueada: la pasa de {@code InCleaning} a {@code Available} y, según
     * la regla 6 de la máquina de estados, a {@code DisabledForRepairs} si se describió un daño o a {@code Reserved} si
     * tiene una llegada pendiente hoy; luego cierra la tarea. Cualquier rechazo o falla revierte todo.
     *
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotFoundException si la tarea no existe
     * @throws com.hospitua.habitaciones.domain.exception.TaskNotOwnedException si la tarea es de otro miembro
     * @throws com.hospitua.habitaciones.domain.exception.TaskAlreadyClosedException si la tarea ya se cerró
     */
    void completeCleaning(CompleteCleaningCommand command);
}
