package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.MaintenancePanelDto;

/** Consultar panel de mantenimiento (plan-consultar-panel-mantenimiento.md T002; FR-001 a FR-011). */
public interface GetMaintenancePanelUseCase {

    /**
     * Solo lee: si el miembro ({@code username}) tiene una tarea de reparación abierta, la devuelve con la fila de su
     * habitación; si no, todas las habitaciones salvo las {@code Inactive}, primero las que requieren intervención.
     */
    MaintenancePanelDto getPanel(String memberId);
}
