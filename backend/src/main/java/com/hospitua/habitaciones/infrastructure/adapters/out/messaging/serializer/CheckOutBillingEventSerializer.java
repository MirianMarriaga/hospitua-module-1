package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Serializador para eventos CHECK_OUT_BILLING hacia Módulo 3 utilizando el sobre EventEnvelope
 * según el Contrato 2 de plan-registrar-check-out.md.
 */
@Component
public class CheckOutBillingEventSerializer implements OutboxMessageSerializer {

    public static final String EVENT_TYPE = "CHECK_OUT_BILLING";
    public static final String TARGET_EVENT_TYPE = "CHECK_OUT";
    public static final String SOURCE_MODULE = "MODULE_1";
    public static final ZoneOffset BOGOTA_OFFSET = ZoneOffset.of("-05:00");

    public record EventEnvelope(
            UUID eventId, String eventType, String occurredAt, String sourceModule, Object payload) {}

    @Override
    public boolean supports(String eventType) {
        return EVENT_TYPE.equalsIgnoreCase(eventType);
    }

    @Override
    public Object serialize(OutboxNotification notification) {
        OffsetDateTime createdAtWithBogotaOffset = notification.createdAt().withOffsetSameInstant(BOGOTA_OFFSET);
        String occurredAt = createdAtWithBogotaOffset.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new EventEnvelope(
                notification.messageId(), TARGET_EVENT_TYPE, occurredAt, SOURCE_MODULE, notification.payload());
    }
}
