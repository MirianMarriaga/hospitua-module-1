package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Adaptador para publicar mensajes en RabbitMQ garantizando Publisher Confirms y mandatory = true.
 * Considera como fallo cualquier NACK, timeout o mensaje devuelto por ausencia de cola receptora.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqOutboxPublisherAdapter {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String routingKey, Object message, UUID messageId) {
        CorrelationData correlationData = new CorrelationData(messageId.toString());

        rabbitTemplate.convertAndSend(RabbitConfig.EVENTS_EXCHANGE, routingKey, message, correlationData);

        try {
            CorrelationData.Confirm confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.ack()) {
                String reason = confirm != null ? confirm.reason() : "Sin confirmación del broker";
                throw new IllegalStateException("Fallo en Publisher Confirm para mensaje " + messageId + ": " + reason);
            }

            // Esperar brevemente por si el retorno por mandatory llega junto con o justo antes del ACK
            for (int i = 0; i < 10; i++) {
                if (correlationData.getReturned() != null) {
                    break;
                }
                Thread.sleep(20);
            }

            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new IllegalStateException(
                        "Mensaje devuelto por el broker (sin cola receptora) para routingKey: " + routingKey);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida para mensaje " + messageId, e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException(
                    "Error al publicar mensaje " + messageId + " en RabbitMQ: " + e.getMessage(), e);
        }
    }
}
