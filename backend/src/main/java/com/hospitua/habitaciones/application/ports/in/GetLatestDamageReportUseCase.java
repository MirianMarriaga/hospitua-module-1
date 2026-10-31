package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.DamageReportViewDto;
import java.util.UUID;

/** "Ver informe" del Personal de mantenimiento (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T013). */
public interface GetLatestDamageReportUseCase {

    /**
     * El reporte que originó el {@code DisabledForRepairs} actual.
     *
     * @throws com.hospitua.habitaciones.domain.exception.RoomNotFoundException si la habitación no existe
     * @throws com.hospitua.habitaciones.domain.exception.DamageReportNotFoundException si no está en
     *     {@code DisabledForRepairs}
     */
    DamageReportViewDto getLatest(UUID roomId);
}
