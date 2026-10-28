package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

/**
 * El broker no confirmó la publicación (nack, sin cola receptora, timeout o interrupción). El worker lo registra
 * como intento fallido.
 */
public class OutboxPublishException extends RuntimeException {

    public OutboxPublishException(String message) {
        super(message);
    }

    public OutboxPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
