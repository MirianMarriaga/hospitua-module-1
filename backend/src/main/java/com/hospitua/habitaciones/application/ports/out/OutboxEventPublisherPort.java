package com.hospitua.habitaciones.application.ports.out;

/**
 * Puerto de salida para encolar eventos asíncronos en la tabla outbox_notification
 * dentro de la misma transacción ACID del flujo invocador.
 */
public interface OutboxEventPublisherPort {

    /**
     * Encola un mensaje en el Outbox con estado PENDING.
     * Genera un messageId (UUIDv4) y obtiene el siguiente sequenceNumber de la secuencia
     * de PostgreSQL correspondiente a la routingKey.
     *
     * @param routingKey clave de enrutamiento destino
     * @param eventType tipo de evento (CHECK_IN, CHECK_OUT, CHECK_OUT_BILLING, MAINTENANCE_NOTICE)
     * @param payload cuerpo del mensaje
     * @return OutboxMessage con messageId y sequenceNumber asignados
     */
    OutboxMessage enqueue(String routingKey, String eventType, Object payload);
}
