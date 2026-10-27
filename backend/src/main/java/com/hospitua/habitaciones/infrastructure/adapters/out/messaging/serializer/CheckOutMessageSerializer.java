package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import org.springframework.stereotype.Component;

/**
 * Serializador para eventos CHECK_OUT hacia Módulo 2 en formato plano.
 */
@Component
public class CheckOutMessageSerializer extends AbstractModule2FlatMessageSerializer {

    public static final String EVENT_TYPE = "CHECK_OUT";

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equalsIgnoreCase(eventType);
    }
}
