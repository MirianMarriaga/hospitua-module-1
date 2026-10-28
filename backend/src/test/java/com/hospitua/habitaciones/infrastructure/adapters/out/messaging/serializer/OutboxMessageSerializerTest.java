package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.application.ports.out.OutboxStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OutboxMessageSerializerTest {

    private final CheckOutBillingEventSerializer billingSerializer = new CheckOutBillingEventSerializer();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"CHECK_IN", "CHECK_OUT", "MAINTENANCE_NOTICE"})
    @DisplayName("Formato plano de Módulo 2: messageId y sequenceNumber primero, luego el payload sin nulos")
    void flatSerializerShouldPrependMessageIdAndSequenceNumber(String eventType) {
        Module2FlatMessageSerializer serializer = new Module2FlatMessageSerializer(eventType);
        UUID messageId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "SCHEDULED");
        payload.put("maintenanceId", "m-1");
        payload.put("previousStartDate", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> message =
                (Map<String, Object>) serializer.serialize(notification(eventType, payload, messageId));

        assertThat(serializer.eventType()).isEqualTo(eventType);
        assertThat(message.keySet()).startsWith("messageId", "sequenceNumber");
        assertThat(message)
                .containsEntry("messageId", messageId)
                .containsEntry("sequenceNumber", 7L)
                .containsEntry("action", "SCHEDULED")
                .containsEntry("maintenanceId", "m-1")
                .doesNotContainKey("previousStartDate");
    }

    private static OutboxNotification notification(String eventType, Object payload, UUID messageId) {
        OffsetDateTime now = OffsetDateTime.now();
        return new OutboxNotification(
                UUID.randomUUID(),
                eventType,
                "habitacion.x",
                payload,
                messageId,
                7L,
                OutboxStatus.PENDING,
                0,
                now,
                null,
                now,
                null);
    }

    @Test
    @DisplayName(
            "CheckOutBillingEventSerializer arma EventEnvelope con eventId, occurredAt con -05:00 y sin sequenceNumber")
    void billingSerializerShouldCreateEventEnvelope() {
        UUID messageId = UUID.randomUUID();
        OffsetDateTime createdAtUtc = OffsetDateTime.of(2026, 10, 11, 16, 20, 40, 0, ZoneOffset.UTC);
        Map<String, Object> billingPayload = Map.of("stayId", "stay-123", "reservationRef", "RSV-300");

        OutboxNotification notification = new OutboxNotification(
                UUID.randomUUID(),
                "CHECK_OUT_BILLING",
                "facturacion.checkout",
                billingPayload,
                messageId,
                99L,
                OutboxStatus.PENDING,
                0,
                createdAtUtc,
                null,
                createdAtUtc,
                null);

        assertThat(billingSerializer.eventType()).isEqualTo("CHECK_OUT_BILLING");
        Object serialized = billingSerializer.serialize(notification);

        assertThat(serialized).isInstanceOf(CheckOutBillingEventSerializer.EventEnvelope.class);
        CheckOutBillingEventSerializer.EventEnvelope envelope =
                (CheckOutBillingEventSerializer.EventEnvelope) serialized;

        assertThat(envelope.eventId()).isEqualTo(messageId);
        assertThat(envelope.eventType()).isEqualTo("CHECK_OUT");
        assertThat(envelope.sourceModule()).isEqualTo("MODULE_1");
        assertThat(envelope.occurredAt()).isEqualTo("2026-10-11T11:20:40-05:00");
        assertThat(envelope.payload()).isEqualTo(billingPayload);
    }
}
