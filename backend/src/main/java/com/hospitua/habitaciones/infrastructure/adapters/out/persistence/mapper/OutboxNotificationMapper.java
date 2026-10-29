package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.application.ports.out.OutboxNotification;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.OutboxNotificationJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre OutboxNotificationJpaEntity y el modelo de puerto OutboxNotification.
 */
@Component
public class OutboxNotificationMapper {

    public OutboxNotification toDomain(OutboxNotificationJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new OutboxNotification(
                entity.getId(),
                entity.getEventType(),
                entity.getRoutingKey(),
                entity.getPayload(),
                entity.getMessageId(),
                entity.getSequenceNumber(),
                entity.getStatus(),
                entity.getRetryCount(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getPublishedAt());
    }

    public OutboxNotificationJpaEntity toEntity(OutboxNotification domain) {
        if (domain == null) {
            return null;
        }
        return new OutboxNotificationJpaEntity(
                domain.id(),
                domain.eventType(),
                domain.routingKey(),
                domain.payload(),
                domain.messageId(),
                domain.sequenceNumber(),
                domain.status(),
                domain.retryCount(),
                domain.nextAttemptAt(),
                domain.lastError(),
                domain.createdAt(),
                domain.publishedAt());
    }
}
