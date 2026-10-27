package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import com.hospitua.habitaciones.application.dto.MaintenanceNoticePayload;
import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Clase base para serializadores de mensajes planos hacia Módulo 2.
 * Agrega messageId y sequenceNumber al payload sin modificar la estructura original y omite valores nulos.
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
            mapPayload.forEach((k, v) -> {
                if (v != null) {
                    messageMap.put(String.valueOf(k), v);
                }
            });
        } else if (payload instanceof MaintenanceNoticePayload notice) {
            messageMap.put("maintenanceId", notice.maintenanceId());
            messageMap.put("action", notice.action().name());
            messageMap.put("roomId", notice.roomId());
            messageMap.put("categoryRoom", notice.categoryRoom());
            messageMap.put("startDate", notice.startDate());
            messageMap.put("endDate", notice.endDate());
            if (notice.previousStartDate() != null) {
                messageMap.put("previousStartDate", notice.previousStartDate());
            }
            if (notice.previousEndDate() != null) {
                messageMap.put("previousEndDate", notice.previousEndDate());
            }
        } else if (payload != null) {
            messageMap.put("data", payload);
        }

        return messageMap;
    }
}
