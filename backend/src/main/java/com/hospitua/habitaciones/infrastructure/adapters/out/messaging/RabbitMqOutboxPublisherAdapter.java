package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en {@code hospitua.events} con Publisher Confirms y {@code mandatory = true}. Un nack, un mensaje devuelto
 * por falta de cola receptora o la falta de confirmación lanzan {@link OutboxPublishException}.
 *
 * <p>Spring AMQP registra el mensaje devuelto en {@link CorrelationData} antes de completar la confirmación, así que
 * basta con revisarlo después del ack.
 */
@Component
@RequiredArgsConstructor
public class RabbitMqOutboxPublisherAdapter {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object message, UUID messageId) {
        CorrelationData correlation = new CorrelationData(messageId.toString());
        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, routingKey, message, correlation);
        try {
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack()) {
                throw new OutboxPublishException("El broker rechazó el mensaje " + messageId + ": " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new OutboxPublishException(
                        "Mensaje devuelto por el broker (sin cola receptora) para " + routingKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxPublishException("Publicación interrumpida del mensaje " + messageId, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new OutboxPublishException("Sin confirmación del broker para el mensaje " + messageId, e);
        }
    }
}
