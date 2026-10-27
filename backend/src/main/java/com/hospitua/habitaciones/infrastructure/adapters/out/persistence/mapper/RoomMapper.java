package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.Room;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre la entidad de dominio Room y la entidad JPA RoomJpaEntity.
 */
@Component
public class RoomMapper {

    public Room toDomain(RoomJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Room(
                entity.getId(),
                entity.getRoomNumber(),
                entity.getFloor(),
                entity.getCategoryRoom(),
                entity.getMaxCapacity(),
                entity.getBaseRate(),
                entity.getStatus(),
                entity.getReservedByReservationRef(),
                entity.getVersion());
    }

    public RoomJpaEntity toEntity(Room domain) {
        if (domain == null) {
            return null;
        }
        return new RoomJpaEntity(
                domain.getId(),
                domain.getRoomNumber(),
                domain.getFloor(),
                domain.getCategoryRoom(),
                domain.getMaxCapacity(),
                domain.getBaseRate(),
                domain.getStatus(),
                domain.getReservedByReservationRef(),
                domain.getVersion());
    }
}
