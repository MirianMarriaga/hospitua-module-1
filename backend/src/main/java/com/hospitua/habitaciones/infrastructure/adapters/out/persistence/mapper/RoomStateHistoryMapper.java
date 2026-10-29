package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.RoomStateHistory;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomStateHistoryJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre la entidad de dominio RoomStateHistory y la entidad JPA RoomStateHistoryJpaEntity.
 */
@Component
public class RoomStateHistoryMapper {

    public RoomStateHistory toDomain(RoomStateHistoryJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RoomStateHistory(
                entity.getId(),
                entity.getRoomId(),
                entity.getStatus(),
                entity.getPreviousStatus(),
                entity.getStartDateTime(),
                entity.getEndDateTime(),
                entity.getActorId(),
                entity.getSourceFlow(),
                entity.getReservationRef());
    }

    public RoomStateHistoryJpaEntity toEntity(RoomStateHistory domain) {
        if (domain == null) {
            return null;
        }
        return new RoomStateHistoryJpaEntity(
                domain.getId(),
                domain.getRoomId(),
                domain.getStatus(),
                domain.getPreviousStatus(),
                domain.getStartDateTime(),
                domain.getEndDateTime(),
                domain.getActorId(),
                domain.getSourceFlow(),
                domain.getReservationRef());
    }
}
