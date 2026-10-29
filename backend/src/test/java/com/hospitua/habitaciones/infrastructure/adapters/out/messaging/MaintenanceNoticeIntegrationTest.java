package com.hospitua.habitaciones.infrastructure.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.application.ports.out.OutboxNotificationRepositoryPort;
import com.hospitua.habitaciones.application.service.MaintenanceNoticePublisher;
import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.domain.model.RoomCategory;
import com.hospitua.habitaciones.domain.model.RoomStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MaintenanceNoticeIntegrationTest {

    @Autowired
    private MaintenanceNoticePublisher publisher;

    @Autowired
    private OutboxNotificationRepositoryPort notificationRepositoryPort;

    @Autowired
    private OutboxSerializerRegistry serializers;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Clock clock;

    private Room room;

    @BeforeEach
    void setUp() {
        room = new Room(
                UUID.randomUUID(),
                "404",
                4,
                RoomCategory.BOUTIQUE,
                2,
                BigDecimal.valueOf(250000),
                RoomStatus.Available,
                null,
                1);
    }

    @Test
    @DisplayName("publishScheduled persiste outbox con payload plano sin previousStartDate/EndDate")
    void publishScheduled_shouldPersistOutboxNotificationWithoutPreviousDates() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID maintenanceId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 15);

        tx.executeWithoutResult(status -> publisher.publishScheduled(maintenanceId, room, start, end));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT routing_key, event_type, status, "
                        + "payload->>'action' as action, "
                        + "payload->>'categoryRoom' as category_room, "
                        + "payload->>'roomId' as room_id, "
                        + "payload->>'startDate' as start_date, "
                        + "payload->>'endDate' as end_date, "
                        + "payload->>'previousStartDate' as prev_start_date, "
                        + "payload->>'previousEndDate' as prev_end_date "
                        + "FROM outbox_notification "
                        + "WHERE routing_key = 'habitacion.mantenimiento' AND payload->>'maintenanceId' = ?",
                maintenanceId.toString());

        assertThat(row.get("routing_key")).isEqualTo("habitacion.mantenimiento");
        assertThat(row.get("event_type")).isEqualTo("MAINTENANCE_NOTICE");
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("action")).isEqualTo("SCHEDULED");
        assertThat(row.get("category_room")).isEqualTo("BOUTIQUE");
        assertThat(row.get("room_id")).isEqualTo(room.getId().toString());
        assertThat(row.get("start_date")).isEqualTo("2026-11-10");
        assertThat(row.get("end_date")).isEqualTo("2026-11-15");
        assertThat(row.get("prev_start_date")).isNull();
        assertThat(row.get("prev_end_date")).isNull();

        // Verificar que el serializador produce el mensaje plano para RabbitMQ sin campos nulos
        List<OutboxNotification> pending = notificationRepositoryPort.findPendingNotifications(
                OffsetDateTime.now(clock).plusDays(1), 100);
        OutboxNotification notification = pending.stream()
                .filter(n -> n.eventType().equals("MAINTENANCE_NOTICE"))
                .findFirst()
                .orElseThrow();

        @SuppressWarnings("unchecked")
        Map<String, Object> serialized = (Map<String, Object>)
                serializers.forType("MAINTENANCE_NOTICE").orElseThrow().serialize(notification);
        assertThat(serialized).containsEntry("action", "SCHEDULED");
        assertThat(serialized).containsEntry("categoryRoom", "BOUTIQUE");
        assertThat(serialized).containsKey("messageId");
        assertThat(serialized).containsKey("sequenceNumber");
        assertThat(serialized).doesNotContainKey("previousStartDate");
        assertThat(serialized).doesNotContainKey("previousEndDate");
    }

    @Test
    @DisplayName("publishUpdated persiste outbox con payload plano incluyendo previousStartDate y previousEndDate")
    void publishUpdated_shouldPersistOutboxNotificationWithPreviousDates() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID maintenanceId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 18);
        LocalDate prevStart = LocalDate.of(2026, 11, 10);
        LocalDate prevEnd = LocalDate.of(2026, 11, 15);

        tx.executeWithoutResult(
                status -> publisher.publishUpdated(maintenanceId, room, start, end, prevStart, prevEnd));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT routing_key, event_type, "
                        + "payload->>'action' as action, "
                        + "payload->>'previousStartDate' as prev_start_date, "
                        + "payload->>'previousEndDate' as prev_end_date "
                        + "FROM outbox_notification "
                        + "WHERE routing_key = 'habitacion.mantenimiento' AND payload->>'maintenanceId' = ?",
                maintenanceId.toString());

        assertThat(row.get("action")).isEqualTo("UPDATED");
        assertThat(row.get("prev_start_date")).isEqualTo("2026-11-10");
        assertThat(row.get("prev_end_date")).isEqualTo("2026-11-15");
    }

    @Test
    @DisplayName("publishCancelled persiste outbox con action CANCELLED")
    void publishCancelled_shouldPersistOutboxNotification() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID maintenanceId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 27);

        tx.executeWithoutResult(status -> publisher.publishCancelled(maintenanceId, room, today, today));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT routing_key, event_type, "
                        + "payload->>'action' as action, "
                        + "payload->>'previousStartDate' as prev_start_date "
                        + "FROM outbox_notification "
                        + "WHERE routing_key = 'habitacion.mantenimiento' AND payload->>'maintenanceId' = ?",
                maintenanceId.toString());

        assertThat(row.get("action")).isEqualTo("CANCELLED");
        assertThat(row.get("prev_start_date")).isNull();
    }

    @Test
    @DisplayName("Rollback en la transacción invocadora no deja ninguna fila en outbox_notification")
    void rollbackShouldLeaveNoRowInOutbox() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID maintenanceId = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 10, 27);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                    publisher.publishScheduled(maintenanceId, room, today, today);
                    throw new RuntimeException("Forzar rollback para prueba");
                }))
                .isInstanceOf(RuntimeException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_notification WHERE payload->>'maintenanceId' = ?",
                Integer.class,
                maintenanceId.toString());

        assertThat(count).isEqualTo(0);
    }
}
