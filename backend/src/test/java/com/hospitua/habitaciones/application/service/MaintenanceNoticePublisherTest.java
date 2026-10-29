package com.hospitua.habitaciones.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.hospitua.habitaciones.application.dto.MaintenanceNoticePayload;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.domain.model.MaintenanceNoticeAction;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class MaintenanceNoticePublisherTest {

    private OutboxEventPublisherPort outboxEventPublisherPort;
    private MaintenanceNoticePublisher publisher;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        outboxEventPublisherPort = Mockito.mock(OutboxEventPublisherPort.class);
        publisher = new MaintenanceNoticePublisher(outboxEventPublisherPort);

        testRoom = new Room(
                UUID.randomUUID(),
                "301",
                3,
                RoomCategory.SUITE,
                2,
                BigDecimal.valueOf(150000),
                RoomStatus.Available,
                null,
                1);
    }

    @Test
    @DisplayName("publishScheduled encola payload con action=SCHEDULED y fechas sin previous")
    void publishScheduled_shouldEnqueueCorrectPayload() {
        UUID maintenanceId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 11, 5);

        publisher.publishScheduled(maintenanceId, testRoom, startDate, endDate);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisherPort)
                .enqueue(eq("habitacion.mantenimiento"), eq("MAINTENANCE_NOTICE"), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isInstanceOf(MaintenanceNoticePayload.class);
        MaintenanceNoticePayload payload = (MaintenanceNoticePayload) payloadCaptor.getValue();

        assertThat(payload.maintenanceId()).isEqualTo(maintenanceId);
        assertThat(payload.action()).isEqualTo(MaintenanceNoticeAction.SCHEDULED);
        assertThat(payload.roomId()).isEqualTo(testRoom.getId());
        assertThat(payload.categoryRoom()).isEqualTo("SUITE");
        assertThat(payload.startDate()).isEqualTo(startDate);
        assertThat(payload.endDate()).isEqualTo(endDate);
        assertThat(payload.previousStartDate()).isNull();
        assertThat(payload.previousEndDate()).isNull();
    }

    @Test
    @DisplayName("publishUpdated encola payload con action=UPDATED e incluye previousStartDate y previousEndDate")
    void publishUpdated_shouldEnqueueCorrectPayloadWithPreviousDates() {
        UUID maintenanceId = UUID.randomUUID();
        LocalDate startDate = LocalDate.of(2026, 11, 1);
        LocalDate endDate = LocalDate.of(2026, 11, 8);
        LocalDate prevStart = LocalDate.of(2026, 11, 1);
        LocalDate prevEnd = LocalDate.of(2026, 11, 5);

        publisher.publishUpdated(maintenanceId, testRoom, startDate, endDate, prevStart, prevEnd);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisherPort)
                .enqueue(eq("habitacion.mantenimiento"), eq("MAINTENANCE_NOTICE"), payloadCaptor.capture());

        MaintenanceNoticePayload payload = (MaintenanceNoticePayload) payloadCaptor.getValue();
        assertThat(payload.maintenanceId()).isEqualTo(maintenanceId);
        assertThat(payload.action()).isEqualTo(MaintenanceNoticeAction.UPDATED);
        assertThat(payload.roomId()).isEqualTo(testRoom.getId());
        assertThat(payload.categoryRoom()).isEqualTo("SUITE");
        assertThat(payload.startDate()).isEqualTo(startDate);
        assertThat(payload.endDate()).isEqualTo(endDate);
        assertThat(payload.previousStartDate()).isEqualTo(prevStart);
        assertThat(payload.previousEndDate()).isEqualTo(prevEnd);
    }

    @Test
    @DisplayName("publishCancelled encola payload con action=CANCELLED y sin previous")
    void publishCancelled_shouldEnqueueCorrectPayload() {
        UUID maintenanceId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 27);

        publisher.publishCancelled(maintenanceId, testRoom, today, today);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisherPort)
                .enqueue(eq("habitacion.mantenimiento"), eq("MAINTENANCE_NOTICE"), payloadCaptor.capture());

        MaintenanceNoticePayload payload = (MaintenanceNoticePayload) payloadCaptor.getValue();
        assertThat(payload.maintenanceId()).isEqualTo(maintenanceId);
        assertThat(payload.action()).isEqualTo(MaintenanceNoticeAction.CANCELLED);
        assertThat(payload.roomId()).isEqualTo(testRoom.getId());
        assertThat(payload.categoryRoom()).isEqualTo("SUITE");
        assertThat(payload.startDate()).isEqualTo(today);
        assertThat(payload.endDate()).isEqualTo(today);
        assertThat(payload.previousStartDate()).isNull();
        assertThat(payload.previousEndDate()).isNull();
    }

    @Test
    @DisplayName("Validación de nulos al invocar publishScheduled")
    void shouldThrowWhenNullParameters() {
        UUID maintenanceId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 27);

        assertThatThrownBy(() -> publisher.publishScheduled(null, testRoom, date, date))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> publisher.publishScheduled(maintenanceId, null, date, date))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> publisher.publishScheduled(maintenanceId, testRoom, null, date))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> publisher.publishScheduled(maintenanceId, testRoom, date, null))
                .isInstanceOf(NullPointerException.class);
    }
}
