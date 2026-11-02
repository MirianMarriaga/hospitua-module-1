package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.ReactivateRoomCommand;
import com.hospitua.habitaciones.application.dto.ReactivationResultDto;

/** Revertir la baja de una habitación {@code Inactive} (plan-marcar-habitacion-como-disponible.md, FR-002). */
public interface ReactivateRoomUseCase {

    ReactivationResultDto reactivate(ReactivateRoomCommand command);
}
