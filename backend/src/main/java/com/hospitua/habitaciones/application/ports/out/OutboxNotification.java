package com.hospitua.habitaciones.application.ports.out;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Representación inmutable de una fila de outbox_notification para el worker de despacho.
 */
public record OutboxNotification(
        UUID id,
        String eventType,
        String routingKey,
        Object payload,
        UUID messageId,
        long sequenceNumber,
        OutboxStatus status,
        int retryCount,
        OffsetDateTime nextAttemptAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt) {}
