package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.DecommissionReason;
import com.hospitua.habitaciones.domain.model.RoomDecommission;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomDecommissionJpaEntity;
import org.springframework.stereotype.Component;

/** Mapper entre la entidad de dominio RoomDecommission y la entidad JPA RoomDecommissionJpaEntity. */
@Component
public class RoomDecommissionMapper {

    public RoomDecommission toDomain(RoomDecommissionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RoomDecommission(
                entity.getId(),
                entity.getRoomId(),
                DecommissionReason.valueOf(entity.getReasonCategory()),
                entity.getReasonDetail(),
                entity.getDecommissionedBy(),
                entity.getDecommissionDateTime(),
                entity.getReactivatedBy(),
                entity.getReactivationDateTime());
    }

    public RoomDecommissionJpaEntity toEntity(RoomDecommission domain) {
        if (domain == null) {
            return null;
        }
        return new RoomDecommissionJpaEntity(
                domain.getId(),
                domain.getRoomId(),
                domain.getReasonCategory().name(),
                domain.getReasonDetail(),
                domain.getDecommissionedBy(),
                domain.getDecommissionDateTime(),
                domain.getReactivatedBy(),
                domain.getReactivationDateTime());
    }
}
