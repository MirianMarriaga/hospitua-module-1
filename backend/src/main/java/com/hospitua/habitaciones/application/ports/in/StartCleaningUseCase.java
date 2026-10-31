package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.CleaningTaskStartedDto;
import com.hospitua.habitaciones.application.dto.StartCleaningCommand;

/** Marcar habitación en limpieza (plan-marcar-habitacion-en-limpieza.md T012; FR-001 a FR-009). */
public interface StartCleaningUseCase {

    /**
     * En una sola transacción, con la habitación bloqueada: pasa la habitación de {@code PendingCleaning} o
     * {@code Available} a {@code InCleaning}, registra el periodo en el historial y crea la tarea del miembro con la
     * misma marca de inicio. Cualquier rechazo o falla revierte todo.
     */
    CleaningTaskStartedDto startCleaning(StartCleaningCommand command);
}
