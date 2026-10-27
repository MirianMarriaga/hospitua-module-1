package com.hospitua.habitaciones.infrastructure.adapters.out.messaging.serializer;

import static org.assertj.core.api.Assertions.assertThat;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.application.ports.out.OutboxStatus;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxMessageSerializerTest {

    private final CheckInMessageSerializer checkInSerializer = new CheckInMessageSerializer();
    private final CheckOutMessageSerializer checkOutSerializer = new CheckOutMessageSerializer();
    private final MaintenanceNoticeMessageSerializer maintenanceSerializer = new MaintenanceNoticeMessageSerializer();
    private final CheckOutBillingEventSerializer billingSerializer = new CheckOutBillingEventSerializer();

    @Test
    @DisplayName("CheckInMessageSerializer agrega messageId y sequenceNumber al payload plano")
    void checkInSerializerShouldAddMessageIdAndSequenceNumber() {
        UUID messageId = UUID.randomUUID();
        OutboxNotification notification = new OutboxNotification(
                UUID.randomUUID(),
                "CHECK_IN",
                "habitacion.checkin",
                Map.of("reservationRef", "RSV-100", "movementType", "ENTRY"),
                messageId,
                15L,
                OutboxStatus.PENDING,
                0,
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now(),
                null);

        assertThat(checkInSerializer.supports("CHECK_IN")).isTrue();
        Object serialized = checkInSerializer.serialize(notification);

        assertThat(serialized).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) serialized;

        assertThat(map.get("messageId")).isEqualTo(messageId);
        assertThat(map.get("sequenceNumber")).isEqualTo(15L);
        assertThat(map.get("reservationRef")).isEqualTo("RSV-100");
        assertThat(map.get("movementType")).isEqualTo("ENTRY");
    }

    @Test
    @DisplayName("CheckOutMessageSerializer agrega messageId y sequenceNumber al payload plano")
    void checkOutSerializerShouldAddMessageIdAndSequenceNumber() {
        UUID messageId = UUID.randomUUID();
        OutboxNotification notification = new OutboxNotification(
                UUID.randomUUID(),
                "CHECK_OUT",
                "habitacion.checkout",
                Map.of("reservationRef", "RSV-200", "movementType", "DEPARTURE"),
                messageId,
                42L,
                OutboxStatus.PENDING,
                0,
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now(),
                null);

        assertThat(checkOutSerializer.supports("CHECK_OUT")).isTrue();
        Object serialized = checkOutSerializer.serialize(notification);

        assertThat(serialized).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) serialized;

        assertThat(map.get("messageId")).isEqualTo(messageId);
        assertThat(map.get("sequenceNumber")).isEqualTo(42L);
        assertThat(map.get("reservationRef")).isEqualTo("RSV-200");
        assertThat(map.get("movementType")).isEqualTo("DEPARTURE");
    }

    @Test
    @DisplayName("MaintenanceNoticeMessageSerializer agrega messageId y sequenceNumber al payload plano")
    void maintenanceSerializerShouldAddMessageIdAndSequenceNumber() {
        UUID messageId = UUID.randomUUID();
        OutboxNotification notification = new OutboxNotification(
                UUID.randomUUID(),
                "MAINTENANCE_NOTICE",
                "habitacion.mantenimiento",
                Map.of("action", "SCHEDULED", "maintenanceId", "m-1"),
                messageId,
                7L,
                OutboxStatus.PENDING,
                0,
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now(),
                null);

        assertThat(maintenanceSerializer.supports("MAINTENANCE_NOTICE")).isTrue();
        Object serialized = maintenanceSerializer.serialize(notification);

        assertThat(serialized).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) serialized;

        assertThat(map.get("messageId")).isEqualTo(messageId);
        assertThat(map.get("sequenceNumber")).isEqualTo(7L);
        assertThat(map.get("action")).isEqualTo("SCHEDULED");
        assertThat(map.get("maintenanceId")).isEqualTo("m-1");
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

        assertThat(billingSerializer.supports("CHECK_OUT_BILLING")).isTrue();
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
