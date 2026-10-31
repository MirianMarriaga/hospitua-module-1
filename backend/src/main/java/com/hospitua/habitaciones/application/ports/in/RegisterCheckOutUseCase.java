package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.CheckOutResultDto;
import com.hospitua.habitaciones.application.dto.RegisterCheckOutCommand;

/** Registro del Check-Out por habitación (plan-registrar-check-out.md T010; FR-008 a FR-016). */
public interface RegisterCheckOutUseCase {

    /**
     * En una sola transacción: exige la confirmación, repite las reglas del Paso 1 con la habitación bloqueada, cierra
     * la estancia, pasa la habitación a {@code PendingCleaning}, audita el Check-Out y encola los mensajes para Módulo 2
     * y Módulo 3. Cualquier rechazo revierte todo.
     */
    CheckOutResultDto registerCheckOut(RegisterCheckOutCommand command);
}
