package com.hospitua.habitaciones.application.ports.in;

import com.hospitua.habitaciones.application.dto.DailyReservationListMessageDto;
import com.hospitua.habitaciones.application.dto.DailyReservationUpdateMessageDto;
import com.hospitua.habitaciones.domain.model.DailyReservationIngestionOutcome;

/**
 * Ingesta de la cola diaria de reservas en la copia local (plan-consultar-reservas.md, FR-001 a FR-003 y FR-009). Cada
 * mensaje se procesa en una transacción: un fallo la revierte entera para que el listener lo reintente.
 */
public interface IngestDailyReservationsUseCase {

    /** Purga la copia local, libera las habitaciones que dejan de figurar y aparta las de la nueva lista. */
    DailyReservationIngestionOutcome ingestDailyList(DailyReservationListMessageDto message);

    /** Aplica un {@code ADDED}, {@code UPDATED} o {@code REMOVED} respetando el orden por reserva. */
    DailyReservationIngestionOutcome ingestUpdate(DailyReservationUpdateMessageDto message);
}
