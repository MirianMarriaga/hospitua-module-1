package com.hospitua.habitaciones.application.ports.out;

/**
 * Estados del ciclo de vida de una notificación en la tabla outbox_notification.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
