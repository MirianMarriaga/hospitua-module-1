package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import org.springframework.stereotype.Component;

/**
 * Serializador para eventos MAINTENANCE_NOTICE hacia Módulo 2 en formato plano.
 */
@Component
public class MaintenanceNoticeMessageSerializer extends AbstractModule2FlatMessageSerializer {

    public static final String EVENT_TYPE = "MAINTENANCE_NOTICE";

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equalsIgnoreCase(eventType);
    }
}
