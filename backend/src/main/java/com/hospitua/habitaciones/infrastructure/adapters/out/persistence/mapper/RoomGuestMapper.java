package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.RoomGuest;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomGuestJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre la entidad inmutable de dominio RoomGuest y la entidad JPA RoomGuestJpaEntity.
 */
@Component
public class RoomGuestMapper {

    public RoomGuest toDomain(RoomGuestJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RoomGuest(
                entity.getId(),
                entity.getStayId(),
                entity.getFirstName(),
                entity.getLastName(),
                entity.getDocumentType(),
                entity.getDocumentNumber(),
                entity.getNationality(),
                entity.getBirthDate(),
                entity.getOriginPlace(),
                entity.getDestinationPlace(),
                entity.isReservationGuest());
    }

    public RoomGuestJpaEntity toEntity(RoomGuest domain) {
        if (domain == null) {
            return null;
        }
        return new RoomGuestJpaEntity(
                domain.getId(),
                domain.getStayId(),
                domain.getFirstName(),
                domain.getLastName(),
                domain.getDocumentType(),
                domain.getDocumentNumber(),
                domain.getNationality(),
                domain.getBirthDate(),
                domain.getOriginPlace(),
                domain.getDestinationPlace(),
                domain.isReservationGuest());
    }
}
