package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.TechnicalBlockViewDto;
import java.util.UUID;

/** "Ver informe" del bloqueo técnico (plan-programar-bloqueo-tecnico-para-habitacion.md T023; FR-009, FR-010). */
public interface GetCurrentTechnicalBlockUseCase {

    /** El informe {@code Applied} de la habitación o, si no tiene, su {@code Scheduled} de inicio más temprano. */
    TechnicalBlockViewDto getCurrent(UUID roomId);
}
