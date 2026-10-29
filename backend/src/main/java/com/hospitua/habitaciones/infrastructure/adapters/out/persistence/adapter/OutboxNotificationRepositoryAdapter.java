package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.OutboxEventPublisherPort;
import com.hospitua.habitaciones.application.ports.out.OutboxMessage;
import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.application.ports.out.OutboxNotificationRepositoryPort;
import com.hospitua.habitaciones.application.ports.out.OutboxStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.OutboxNotificationJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.OutboxNotificationMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataOutboxNotificationJpaRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adaptador de persistencia para el mecanismo Outbox.
 * Implementa OutboxEventPublisherPort para encolar dentro de la transacción del invocador (MANDATORY),
 * y OutboxNotificationRepositoryPort para el sondeo y actualización atómica del worker.
 */
@Component
@RequiredArgsConstructor
public class OutboxNotificationRepositoryAdapter implements OutboxEventPublisherPort, OutboxNotificationRepositoryPort {

    /** Secuencia de PostgreSQL de cada cola (V1): el sequenceNumber nunca se reinicia por routing key. */
    private static final Map<String, String> SEQUENCE_BY_ROUTING_KEY = Map.of(
            "habitacion.checkin", "outbox_seq_habitacion_checkin",
            "habitacion.checkout", "outbox_seq_habitacion_checkout",
            "facturacion.checkout", "outbox_seq_facturacion_checkout",
            "habitacion.mantenimiento", "outbox_seq_habitacion_mantenimiento");

    private final SpringDataOutboxNotificationJpaRepository repository;
    private final OutboxNotificationMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxMessage enqueue(String routingKey, String eventType, Object payload) {
        String sequenceName = SEQUENCE_BY_ROUTING_KEY.get(routingKey);
        if (sequenceName == null) {
            throw new IllegalArgumentException("No hay secuencia de Outbox para la routing key: " + routingKey);
        }
        long sequenceNumber = jdbcTemplate.queryForObject("SELECT nextval('" + sequenceName + "')", Long.class);
        UUID messageId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(clock);

        OutboxNotificationJpaEntity entity = new OutboxNotificationJpaEntity(
                UUID.randomUUID(),
                eventType,
                routingKey,
                payload,
                messageId,
                sequenceNumber,
                OutboxStatus.PENDING,
                0,
                now,
                null,
                now,
                null);

        repository.save(entity);

        return new OutboxMessage(messageId, sequenceNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxNotification> findPendingNotifications(OffsetDateTime now, int limit) {
        return repository
                .findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, now, Limit.of(limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID id, OffsetDateTime publishedAt) {
        repository.findById(id).ifPresent(entity -> {
            entity.setStatus(OutboxStatus.PUBLISHED);
            entity.setPublishedAt(publishedAt);
            entity.setLastError(null);
            repository.save(entity);
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            UUID id, int retryCount, OffsetDateTime nextAttemptAt, String lastError, OutboxStatus status) {
        repository.findById(id).ifPresent(entity -> {
            entity.setRetryCount(retryCount);
            entity.setNextAttemptAt(nextAttemptAt);
            entity.setLastError(lastError);
            entity.setStatus(status);
            repository.save(entity);
        });
    }
}
