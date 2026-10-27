package com.hospitua.habitaciones.infrastructure.adapters.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hospitua.habitaciones.TestcontainersConfiguration;
import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.application.ports.out.OutboxMessage;
import com.hospitua.habitaciones.application.ports.out.OutboxNotificationRepositoryPort;
import com.hospitua.habitaciones.infrastructure.adapters.out.messaging.OutboxScheduledWorker;
import com.hospitua.habitaciones.infrastructure.config.RabbitConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class OutboxTransactionalIntegrationTest {

    @Autowired
    private OutboxEventPublisherPort outboxPublisherPort;

    @Autowired
    private OutboxNotificationRepositoryPort notificationRepositoryPort;

    @Autowired
    private OutboxScheduledWorker outboxScheduledWorker;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("enqueue guarda PENDING con messageId y sequenceNumber creciente por routing key sin reiniciar")
    void enqueueShouldPersistPendingWithIncreasingSequencePerRoutingKey() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        OutboxMessage msg1 = tx.execute(status ->
                outboxPublisherPort.enqueue(RabbitConfig.ROUTING_KEY_CHECKIN, "CHECK_IN", Map.of("room", "101")));
        OutboxMessage msg2 = tx.execute(status ->
                outboxPublisherPort.enqueue(RabbitConfig.ROUTING_KEY_CHECKIN, "CHECK_IN", Map.of("room", "102")));

        assertThat(msg1).isNotNull();
        assertThat(msg2).isNotNull();
        assertThat(msg1.messageId()).isNotEqualTo(msg2.messageId());
        assertThat(msg2.sequenceNumber()).isGreaterThan(msg1.sequenceNumber());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, routing_key FROM outbox_notification WHERE message_id = ?",
                msg1.messageId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(((Number) row.get("retry_count")).intValue()).isEqualTo(0);
        assertThat(row.get("routing_key")).isEqualTo(RabbitConfig.ROUTING_KEY_CHECKIN);
    }

    @Test
    @DisplayName("enqueue fuera de una transacción falla con MANDATORY; si la transacción se revierte, no queda fila")
    void enqueueOutsideTransactionShouldFailAndRollbackShouldLeaveNoRow() {
        // 1. Fuera de transacción -> falla MANDATORY
        assertThatThrownBy(() -> outboxPublisherPort.enqueue(
                        RabbitConfig.ROUTING_KEY_CHECKOUT, "CHECK_OUT", Map.of("room", "201")))
                .isInstanceOf(IllegalTransactionStateException.class);

        // 2. Transacción que hace rollback -> no queda fila
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID[] messageIdHolder = new UUID[1];

        assertThatThrownBy(() -> tx.execute(status -> {
                    OutboxMessage msg = outboxPublisherPort.enqueue(
                            RabbitConfig.ROUTING_KEY_CHECKOUT, "CHECK_OUT", Map.of("room", "202"));
                    messageIdHolder[0] = msg.messageId();
                    throw new RuntimeException("Forzando rollback de prueba");
                }))
                .isInstanceOf(RuntimeException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_notification WHERE message_id = ?", Integer.class, messageIdHolder[0]);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("El worker publica PENDING, lo marca PUBLISHED y el mensaje llega a su cola con el formato esperado")
    void workerShouldPublishPendingAndMarkPublishedAndDeliverMessage() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        OutboxMessage msg = tx.execute(status -> outboxPublisherPort.enqueue(
                RabbitConfig.ROUTING_KEY_CHECKIN,
                "CHECK_IN",
                Map.of("reservationRef", "RSV-PUB-1", "movementType", "ENTRY")));

        assertThat(msg).isNotNull();

        // Ejecutar worker
        outboxScheduledWorker.processPendingNotifications();

        // Verificar estado PUBLISHED en BD
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, published_at FROM outbox_notification WHERE message_id = ?", msg.messageId());
        assertThat(row.get("status")).isEqualTo("PUBLISHED");
        assertThat(row.get("published_at")).isNotNull();

        // Verificar que llegó a la cola con messageId y sequenceNumber
        @SuppressWarnings("unchecked")
        Map<String, Object> received =
                (Map<String, Object>) rabbitTemplate.receiveAndConvert(RabbitConfig.CHECKIN_QUEUE, 5000);
        assertThat(received).isNotNull();
        assertThat(received.get("messageId")).isEqualTo(msg.messageId().toString());
        assertThat(((Number) received.get("sequenceNumber")).longValue()).isEqualTo(msg.sequenceNumber());
        assertThat(received.get("reservationRef")).isEqualTo("RSV-PUB-1");
    }

    @Test
    @DisplayName("Mensaje sin cola receptora se reintenta, conserva messageId y al agotar reintentos queda FAILED")
    void unroutableMessageShouldRetryAndEventuallyFailWithoutChangingMessageId() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        OutboxMessage msg = tx.execute(status -> outboxPublisherPort.enqueue(
                RabbitConfig.ROUTING_KEY_BILLING_CHECKOUT, "CHECK_OUT_BILLING", Map.of("stayId", "stay-unroutable")));

        assertThat(msg).isNotNull();

        // 1er intento: falla porque facturacion.checkout no tiene cola -> PENDING con retry_count=1
        outboxScheduledWorker.processPendingNotifications();

        Map<String, Object> rowAfterFirstAttempt = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, last_error, next_attempt_at FROM outbox_notification WHERE message_id = ?",
                msg.messageId());
        assertThat(rowAfterFirstAttempt.get("status")).isEqualTo("PENDING");
        assertThat(((Number) rowAfterFirstAttempt.get("retry_count")).intValue())
                .isEqualTo(1);
        assertThat(rowAfterFirstAttempt.get("last_error")).isNotNull();
        assertThat(rowAfterFirstAttempt.get("next_attempt_at")).isNotNull();

        // Forzar next_attempt_at = now y retry_count = 9 para probar agotamiento de max-retries (10)
        jdbcTemplate.update(
                "UPDATE outbox_notification SET next_attempt_at = NOW() - INTERVAL '1 minute', retry_count = 9 WHERE message_id = ?",
                msg.messageId());

        // Ejecutar worker (10mo intento) -> debe pasar a FAILED
        outboxScheduledWorker.processPendingNotifications();

        Map<String, Object> rowFinal = jdbcTemplate.queryForMap(
                "SELECT status, retry_count, last_error, message_id FROM outbox_notification WHERE message_id = ?",
                msg.messageId());
        assertThat(rowFinal.get("status")).isEqualTo("FAILED");
        assertThat(((Number) rowFinal.get("retry_count")).intValue()).isEqualTo(10);
        assertThat(rowFinal.get("last_error")).isNotNull();
        // El messageId debe conservarse exactamente idéntico
        assertThat(rowFinal.get("message_id")).isEqualTo(msg.messageId());
    }

    @Test
    @DisplayName("Un mensaje que falla no frena a los siguientes de la misma cola")
    void failedMessageDoesNotBlockSubsequentMessages() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Encolamos un mensaje unroutable (falla) y uno válido a checkin (éxito)
        OutboxMessage unroutable = tx.execute(status -> outboxPublisherPort.enqueue(
                RabbitConfig.ROUTING_KEY_BILLING_CHECKOUT, "CHECK_OUT_BILLING", Map.of("stayId", "unroutable")));

        OutboxMessage valid = tx.execute(status -> outboxPublisherPort.enqueue(
                RabbitConfig.ROUTING_KEY_CHECKIN, "CHECK_IN", Map.of("room", "success-after-failure")));

        // Ejecutar worker
        outboxScheduledWorker.processPendingNotifications();

        // El unroutable debe quedar en PENDING (o FAILED) con error
        Map<String, Object> rowUnroutable = jdbcTemplate.queryForMap(
                "SELECT status FROM outbox_notification WHERE message_id = ?", unroutable.messageId());
        assertThat(rowUnroutable.get("status")).isIn("PENDING", "FAILED");

        // El válido debe haberse publicado exitosamente
        Map<String, Object> rowValid = jdbcTemplate.queryForMap(
                "SELECT status FROM outbox_notification WHERE message_id = ?", valid.messageId());
        assertThat(rowValid.get("status")).isEqualTo("PUBLISHED");

        // Y llegó a la cola de RabbitMQ
        @SuppressWarnings("unchecked")
        Map<String, Object> received =
                (Map<String, Object>) rabbitTemplate.receiveAndConvert(RabbitConfig.CHECKIN_QUEUE, 5000);
        assertThat(received).isNotNull();
        assertThat(received.get("room")).isEqualTo("success-after-failure");
    }
}
