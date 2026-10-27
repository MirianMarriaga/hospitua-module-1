package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.application.ports.out.OutboxNotificationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.OutboxStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer.OutboxMessageSerializer;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Worker programado para despachar notificaciones pendientes del Outbox hacia RabbitMQ.
 * Cada mensaje se procesa y reintenta de forma independiente para evitar bloqueos en cola.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduledWorker {

    private final OutboxNotificationRepositoryPort repositoryPort;
    private final RabbitMqOutboxPublisherAdapter publisherAdapter;
    private final List<OutboxMessageSerializer> serializers;
    private final OutboxBackoffPolicy backoffPolicy;
    private final Clock clock;

    @Value("${hospitua.outbox.max-retries:10}")
    private int maxRetries;

    @Scheduled(fixedDelayString = "${hospitua.outbox.interval:1000}")
    public void processPendingNotifications() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<OutboxNotification> pending = repositoryPort.findPendingNotifications(now);

        for (OutboxNotification notification : pending) {
            processNotification(notification, now);
        }
    }

    private void processNotification(OutboxNotification notification, OffsetDateTime now) {
        try {
            OutboxMessageSerializer serializer = serializers.stream()
                    .filter(s -> s.supports(notification.eventType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No se encontró serializador para eventType: " + notification.eventType()));

            Object messagePayload = serializer.serialize(notification);
            publisherAdapter.publish(notification.routingKey(), messagePayload, notification.messageId());

            repositoryPort.markPublished(notification.id(), OffsetDateTime.now(clock));
            log.debug(
                    "Notificación {} publicada exitosamente hacia {}",
                    notification.messageId(),
                    notification.routingKey());

        } catch (Exception ex) {
            int newRetryCount = notification.retryCount() + 1;
            String errorMessage = ex.getMessage();

            if (newRetryCount >= maxRetries) {
                log.error(
                        "[OUTBOX_PUBLISH_FAILED] Se agotaron los reintentos ({}) para el mensaje {} (eventType={}): {}",
                        maxRetries,
                        notification.messageId(),
                        notification.eventType(),
                        errorMessage);

                repositoryPort.recordFailure(notification.id(), newRetryCount, null, errorMessage, OutboxStatus.FAILED);
            } else {
                Duration delay = backoffPolicy.calculateDelay(newRetryCount);
                OffsetDateTime nextAttemptAt = now.plus(delay);

                log.warn(
                        "Fallo al publicar mensaje {} (intento {}/{}). Próximo intento en {}: {}",
                        notification.messageId(),
                        newRetryCount,
                        maxRetries,
                        delay,
                        errorMessage);

                repositoryPort.recordFailure(
                        notification.id(), newRetryCount, nextAttemptAt, errorMessage, OutboxStatus.PENDING);
            }
        }
    }
}
