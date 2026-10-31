package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.CleaningPanelDto;

/** Consultar panel de limpieza (plan-consultar-panel-limpieza.md T002; FR-001 a FR-011). */
public interface GetCleaningPanelUseCase {

    /**
     * Solo lee: si el miembro ({@code username}) tiene una tarea abierta, la devuelve con la fila de su habitación;
     * si no, las habitaciones en {@code PendingCleaning} y {@code Available}, primero las pendientes, con su última
     * limpieza.
     */
    CleaningPanelDto getPanel(String memberId);
}
