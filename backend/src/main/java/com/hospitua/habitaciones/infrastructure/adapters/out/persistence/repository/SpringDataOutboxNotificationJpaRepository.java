package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.application.ports.out.OutboxStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.OutboxNotificationJpaEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para outbox_notification.
 */
@Repository
public interface SpringDataOutboxNotificationJpaRepository extends JpaRepository<OutboxNotificationJpaEntity, UUID> {

    List<OutboxNotificationJpaEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status, OffsetDateTime nextAttemptAt, Limit limit);
}
