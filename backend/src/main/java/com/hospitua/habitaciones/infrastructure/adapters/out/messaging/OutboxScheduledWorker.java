package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.application.ports.out.OutboxNotificationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.OutboxStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.OutboxMessageSerializer;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Despacha los mensajes PENDING vencidos del Outbox (T012). Cada mensaje se publica y registra por separado, así un
 * fallo no frena a los siguientes de la cola.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduledWorker {

    private final OutboxNotificationRepositoryPort repository;
    private final OutboxSerializerRegistry serializers;
    private final RabbitMqOutboxPublisherAdapter publisher;
    private final OutboxBackoffPolicy backoffPolicy;
    private final OutboxProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${hospitua.outbox.interval:1000}")
    public void processPendingNotifications() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (OutboxNotification notification : repository.findPendingNotifications(now, properties.batchSize())) {
            process(notification, now);
        }
    }

    private void process(OutboxNotification notification, OffsetDateTime now) {
        OutboxMessageSerializer serializer =
                serializers.forType(notification.eventType()).orElse(null);
        if (serializer == null) {
            markFailed(notification, notification.retryCount(), "Sin serializador para " + notification.eventType());
            return;
        }
        try {
            publisher.publish(notification.routingKey(), serializer.serialize(notification), notification.messageId());
            repository.markPublished(notification.id(), OffsetDateTime.now(clock));
        } catch (RuntimeException ex) {
            recordFailedAttempt(notification, now, ex.getMessage());
        }
    }

    private void recordFailedAttempt(OutboxNotification notification, OffsetDateTime now, String error) {
        int attempts = notification.retryCount() + 1;
        if (attempts >= properties.maxRetries()) {
            markFailed(notification, attempts, error);
            return;
        }
        OffsetDateTime nextAttemptAt = now.plus(backoffPolicy.calculateDelay(attempts));
        log.warn(
                "Fallo al publicar {} (intento {}/{}); reintento a las {}: {}",
                notification.messageId(),
                attempts,
                properties.maxRetries(),
                nextAttemptAt,
                error);
        repository.recordFailure(notification.id(), attempts, nextAttemptAt, error, OutboxStatus.PENDING);
    }

    private void markFailed(OutboxNotification notification, int attempts, String error) {
        log.error(
                "[OUTBOX_PUBLISH_FAILED] Mensaje {} (eventType={}) queda FAILED tras {} intentos: {}",
                notification.messageId(),
                notification.eventType(),
                attempts,
                error);
        repository.recordFailure(notification.id(), attempts, null, error, OutboxStatus.FAILED);
    }
}
