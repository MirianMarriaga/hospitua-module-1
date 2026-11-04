package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.RepairTaskStartedDto;
import com.hospitua.habitaciones.application.dto.StartRepairCommand;

/** Iniciar reparaciones (plan-confirmar-reparacion-finalizada.md T010; FR-011, FR-012). */
public interface StartRepairUseCase {

    /**
     * Con la habitación bloqueada, crea la tarea de reparación del miembro sobre una habitación
     * {@code DisabledForRepairs} o {@code TechnicalBlock}, sin cambiar su estado ni escribir en el historial.
     */
    RepairTaskStartedDto startRepair(StartRepairCommand command);
}
