package com.hospitua.habitaciones.infrastructure.adapters.in.amqp;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/**
 * Mensaje de la cola diaria ilegible o incompleto: va a {@code m1.reservas.diarias.dlq} sin aplicarse ni reintentarse
 * (plan-consultar-reservas.md T014; FR-003).
 */
public class InvalidDailyReservationMessageException extends AmqpRejectAndDontRequeueException {

    public InvalidDailyReservationMessageException(String message) {
        super(message);
    }

    public InvalidDailyReservationMessageException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Valor obligatorio del mensaje. */
    public static <T> T required(T value, String field) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            throw new InvalidDailyReservationMessageException("Falta el campo obligatorio " + field + ".");
        }
        return value;
    }
}
