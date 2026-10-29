package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formato plano de Módulo 2: {@code messageId} y {@code sequenceNumber} seguidos de los campos del payload, sin los
 * nulos. Se instancia una vez por {@code event_type} (ver {@code OutboxConfig}).
 */
public final class Module2FlatMessageSerializer implements OutboxMessageSerializer {

    private final String eventType;

    public Module2FlatMessageSerializer(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public String eventType() {
        return eventType;
    }

    @Override
    public Object serialize(OutboxNotification notification) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", notification.messageId());
        message.put("sequenceNumber", notification.sequenceNumber());
        if (notification.payload() instanceof Map<?, ?> payload) {
            payload.forEach((key, value) -> {
                if (value != null) {
                    message.put(key.toString(), value);
                }
            });
        }
        return message;
    }
}
