package com.hospitua.habitaciones.application.ports.out;

import java.util.UUID;

/**
 * Resultado devuelto al encolar un mensaje en el Outbox.
 * Contiene el identificador único del mensaje y su número de secuencia creciente por routing key.
 */
public record OutboxMessage(UUID messageId, long sequenceNumber) {}
