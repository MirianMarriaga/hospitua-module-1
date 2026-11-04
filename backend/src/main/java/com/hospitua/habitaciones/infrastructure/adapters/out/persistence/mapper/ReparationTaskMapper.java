package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.ReparationTask;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.ReparationTaskJpaEntity;
import org.springframework.stereotype.Component;

/** Mapper entre la entidad de dominio ReparationTask y la entidad JPA ReparationTaskJpaEntity. */
@Component
public class ReparationTaskMapper {

    public ReparationTask toDomain(ReparationTaskJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReparationTask(
                entity.getId(),
                entity.getRoomId(),
                entity.getMaintenanceStaffMemberId(),
                entity.getStartDateTime(),
                entity.getEndDateTime(),
                entity.getOutcome());
    }

    public ReparationTaskJpaEntity toEntity(ReparationTask domain) {
        if (domain == null) {
            return null;
        }
        return new ReparationTaskJpaEntity(
                domain.getId(),
                domain.getRoomId(),
                domain.getMaintenanceStaffMemberId(),
                domain.getStartDateTime(),
                domain.getEndDateTime(),
                domain.getOutcome());
    }
}
