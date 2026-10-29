package com.hospitua.habitaciones.application.ports.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Puerto secundario para que el worker del Outbox consulte y actualice notificaciones.
 */
public interface OutboxNotificationRepositoryPort {

    /**
     * Consulta las notificaciones en estado PENDING cuyo nextAttemptAt sea menor o igual a now,
     * ordenadas por fecha de creación ascendente, como máximo {@code limit} por consulta.
     */
    List<OutboxNotification> findPendingNotifications(OffsetDateTime now, int limit);

    /**
     * Marca una notificación como exitosamente publicada.
     */
    void markPublished(UUID id, OffsetDateTime publishedAt);

    /**
     * Registra un intento fallido de publicación, actualizando reintentos, próximo intento,
     * último error y estado (PENDING o FAILED si se agotaron los reintentos).
     */
    void recordFailure(UUID id, int retryCount, OffsetDateTime nextAttemptAt, String lastError, OutboxStatus status);
}
