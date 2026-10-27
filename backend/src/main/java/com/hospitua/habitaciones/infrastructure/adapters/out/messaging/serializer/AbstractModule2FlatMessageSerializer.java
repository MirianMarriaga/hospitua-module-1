package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clase base para serializadores de mensajes planos hacia Módulo 2.
 * Agrega messageId y sequenceNumber al payload sin modificar la estructura original.
 */
public abstract class AbstractModule2FlatMessageSerializer implements OutboxMessageSerializer {

    @Override
    @SuppressWarnings("unchecked")
    public Object serialize(OutboxNotification notification) {
        Map<String, Object> messageMap = new LinkedHashMap<>();
        messageMap.put("messageId", notification.messageId());
        messageMap.put("sequenceNumber", notification.sequenceNumber());

        Object payload = notification.payload();
        if (payload instanceof Map<?, ?> mapPayload) {
            mapPayload.forEach((k, v) -> messageMap.put(String.valueOf(k), v));
        } else if (payload != null) {
            messageMap.put("data", payload);
        }

        return messageMap;
    }
}
