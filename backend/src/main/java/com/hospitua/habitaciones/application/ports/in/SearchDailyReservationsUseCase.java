package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.PendingArrivalsDto;

/**
 * Llegadas del día pendientes de check-in, solo desde la copia local y sin llamar a Módulo 2
 * (plan-consultar-reservas.md T020; plan-consultar-panel-recepcion.md T011, FR-003, FR-011).
 */
public interface SearchDailyReservationsUseCase {

    /**
     * Reservas con llegada hoy y al menos una habitación sin {@code Stay}, cada una con sus habitaciones pendientes. Un
     * {@code query} nulo o en blanco devuelve todas; si no, filtra sin distinguir mayúsculas ni espacios sobrantes por
     * {@code reservationRef} o documento del titular (exactos) o por parte del nombre o el apellido del titular.
     */
    PendingArrivalsDto searchPendingArrivals(String query);
}
