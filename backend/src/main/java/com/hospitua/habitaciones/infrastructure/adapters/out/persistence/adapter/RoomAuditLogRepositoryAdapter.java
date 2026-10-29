package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.adapter;

import com.hospitua.habitaciones.application.ports.out.RoomAuditLogRepositoryPort;
import com.hospitua.habitaciones.domain.model.RoomAuditEntry;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomAuditLogJpaEntity;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper.RoomAuditLogMapper;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository.SpringDataRoomAuditLogJpaRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adaptador de persistencia para la bitácora de auditoría implementando RoomAuditLogRepositoryPort.
 */
@Component
@RequiredArgsConstructor
public class RoomAuditLogRepositoryAdapter implements RoomAuditLogRepositoryPort {

    private final SpringDataRoomAuditLogJpaRepository repository;
    private final RoomAuditLogMapper mapper;
    private final Clock clock;

    @Override
    public void record(RoomAuditEntry entry) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        RoomAuditLogJpaEntity entity = mapper.toEntity(entry, now);
        repository.save(entity);
    }
}
