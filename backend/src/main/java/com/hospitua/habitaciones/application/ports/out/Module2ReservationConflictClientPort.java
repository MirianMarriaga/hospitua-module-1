package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.ReservationConflictQuery;
import com.hospitua.habitaciones.domain.model.ReservationConflictResult;

/**
 * Consulta a Módulo 2 de las reservas de una habitación en un rango ({@code GET /api/reservations}; consultar-reservas
 * T025). Lanza {@code ExternalModule2UnavailableException} si Módulo 2 no responde o rechaza la consulta.
 */
public interface Module2ReservationConflictClientPort {

    ReservationConflictResult checkConflicts(ReservationConflictQuery query);
}
