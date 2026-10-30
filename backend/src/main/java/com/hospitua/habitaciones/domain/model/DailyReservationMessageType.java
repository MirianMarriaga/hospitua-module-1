package com.hospitua.habitaciones.domain.model;

/**
 * Tipo de mensaje de la cola diaria de reservas. Las routing keys de RabbitMQ son un detalle de infraestructura: las
 * traduce el listener al recibir el mensaje y el mapper de persistencia al guardar {@code message_type}.
 */
public enum DailyReservationMessageType {
    /** Lista del día de las 00:00 ({@code sequenceNumber = 1}). */
    DAILY_LIST,
    /** Actualización {@code ADDED}, {@code UPDATED} o {@code REMOVED} durante el día. */
    UPDATE
}
