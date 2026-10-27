package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;

/**
 * Estrategia de serialización para los mensajes del Outbox según su event_type.
 * Permite cumplir el principio Abierto/Cerrado (OCP) al agregar nuevos tipos de evento.
 */
public interface OutboxMessageSerializer {

    boolean supports(String eventType);

    Object serialize(OutboxNotification notification);
}
