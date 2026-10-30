package com.hospitua.habitaciones.domain.model;

/**
 * Tipo de mensaje de la cola diaria de reservas; se guarda en {@code daily_reservation_message_log.message_type} con
 * su routing key.
 */
public enum DailyReservationMessageType {
    /** Lista del día de las 00:00 ({@code sequenceNumber = 1}). */
    DAILY_LIST("reserva.lista-del-dia"),
    /** Actualización {@code ADDED}, {@code UPDATED} o {@code REMOVED} durante el día. */
    UPDATE("reserva.lista-del-dia.actualizacion");

    private final String routingKey;

    DailyReservationMessageType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }

    public static DailyReservationMessageType fromRoutingKey(String routingKey) {
        for (DailyReservationMessageType type : values()) {
            if (type.routingKey.equals(routingKey)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Routing key de reservas desconocida: " + routingKey);
    }
}
