package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.Stay;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.StayJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre la entidad de dominio Stay y la entidad JPA StayJpaEntity.
 */
@Component
public class StayMapper {

    public Stay toDomain(StayJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Stay(
                entity.getId(),
                entity.getReservationRef(),
                entity.getRoomId(),
                entity.getSource(),
                entity.getCheckInDate(),
                entity.getCheckOutDate(),
                entity.getExpectedCheckinDate(),
                entity.getExpectedCheckoutDate(),
                entity.getTitularFirstName(),
                entity.getTitularLastName(),
                entity.getTitularDocumentNumber(),
                entity.getVersion());
    }

    public StayJpaEntity toEntity(Stay domain) {
        if (domain == null) {
            return null;
        }
        return new StayJpaEntity(
                domain.getId(),
                domain.getReservationRef(),
                domain.getRoomId(),
                domain.getSource(),
                domain.getCheckInDate(),
                domain.getCheckOutDate(),
                domain.getExpectedCheckinDate(),
                domain.getExpectedCheckoutDate(),
                domain.getTitularFirstName(),
                domain.getTitularLastName(),
                domain.getTitularDocumentNumber(),
                domain.getVersion());
    }
}
