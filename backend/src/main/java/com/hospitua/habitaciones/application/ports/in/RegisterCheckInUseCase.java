package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.CheckInResultDto;
import com.hospitua.habitaciones.application.dto.RegisterCheckInCommand;

/** Registro del Check-In por habitación (plan-registrar-check-in.md T016; FR-011 a FR-015). */
public interface RegisterCheckInUseCase {

    /**
     * En una sola transacción: repite las reglas del Paso 1 con la habitación bloqueada, valida los ocupantes, crea la
     * estancia y sus huéspedes, pasa la habitación a {@code Occupied}, audita el Check-In y encola la notificación
     * para Módulo 2. Cualquier rechazo revierte todo.
     */
    CheckInResultDto registerCheckIn(RegisterCheckInCommand command);
}
