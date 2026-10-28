package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;

/**
 * Estrategia que arma el mensaje publicado para un {@code event_type} del Outbox (T012). Un tipo nuevo agrega su
 * serializador sin tocar los existentes.
 */
public interface OutboxMessageSerializer {

    String eventType();

    Object serialize(OutboxNotification notification);
}
