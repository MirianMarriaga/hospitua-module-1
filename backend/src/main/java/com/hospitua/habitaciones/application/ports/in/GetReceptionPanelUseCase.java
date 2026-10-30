package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.domain.model.ReceptionDashboardKpis;

/** Indicadores del panel de Recepción, sin llamar a Módulo 2 (plan-consultar-panel-recepcion.md T022, FR-002). */
public interface GetReceptionPanelUseCase {

    /**
     * Llegadas pendientes de hoy (nulo sin la lista de las 00:00), salidas de hoy, salidas vencidas y habitaciones
     * {@code Occupied}.
     */
    ReceptionDashboardKpis getKpis();
}
