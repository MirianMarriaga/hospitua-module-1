package com.hospitua.habitaciones.domain.model;

/** Resultado de procesar un mensaje de la cola diaria de reservas (plan-consultar-reservas.md FR-003). */
public enum DailyReservationIngestionOutcome {
    /** Se aplicó a la copia local. */
    APPLIED,
    /** Su {@code messageId} ya estaba registrado; no se vuelve a registrar. */
    DUPLICATE,
    /** Su {@code operationalDate} es anterior al día de la copia local; se registra sin aplicarlo. */
    OUTDATED_DAY,
    /** Su secuencia no supera la última aplicada para la reserva en el día; se registra sin aplicarlo. */
    OUTDATED_SEQUENCE
}
