package com.hospitua.habitaciones.domain.model;

/**
 * Los cuatro indicadores del turno, calculados con datos locales (plan-consultar-panel-recepcion.md FR-002). {@code
 * arrivalsToday} es nulo si hoy no llegó la lista de las 00:00, y la interfaz muestra "—" (FR-011).
 */
public record ReceptionDashboardKpis(
        Integer arrivalsToday, long departuresToday, long overdueDepartures, long occupiedRooms) {}
