package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.CleaningTask;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.CleaningTaskJpaEntity;
import org.springframework.stereotype.Component;

/** Mapper entre la entidad de dominio CleaningTask y la entidad JPA CleaningTaskJpaEntity. */
@Component
public class CleaningTaskMapper {

    public CleaningTask toDomain(CleaningTaskJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new CleaningTask(
                entity.getId(),
                entity.getRoomId(),
                entity.getCleaningStaffMemberId(),
                entity.getStartDateTime(),
                entity.getEndDateTime(),
                entity.getOutcome());
    }

    public CleaningTaskJpaEntity toEntity(CleaningTask domain) {
        if (domain == null) {
            return null;
        }
        return new CleaningTaskJpaEntity(
                domain.getId(),
                domain.getRoomId(),
                domain.getCleaningStaffMemberId(),
                domain.getStartDateTime(),
                domain.getEndDateTime(),
                domain.getOutcome());
    }
}
