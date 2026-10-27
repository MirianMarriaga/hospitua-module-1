package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomAuditLogJpaEntity;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mapper para convertir entradas de auditoría RoomAuditEntry a la entidad JPA RoomAuditLogJpaEntity.
 */
@Component
public class RoomAuditLogMapper {

    public RoomAuditLogJpaEntity toEntity(RoomAuditEntry entry, OffsetDateTime eventDateTime) {
        if (entry == null) {
            return null;
        }
        return new RoomAuditLogJpaEntity(
                UUID.randomUUID(), entry.roomId(), entry.eventType(), entry.actorId(), entry.details(), eventDateTime);
    }
}
