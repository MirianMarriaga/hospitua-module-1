package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.StaffAccount;
import com.hospitua.habitaciones.domain.model.StaffArea;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.UserAccountJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper entre la entidad de dominio StaffAccount y la entidad JPA UserAccountJpaEntity (T022).
 */
@Component
public class StaffAccountMapper {

    public StaffAccount toDomain(UserAccountJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        StaffArea area = entity.getArea() != null ? StaffArea.valueOf(entity.getArea()) : null;
        return new StaffAccount(
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getFullName(),
                area,
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy(),
                entity.getDeactivatedAt(),
                entity.getDeactivatedBy(),
                entity.getVersion());
    }

    public UserAccountJpaEntity toEntity(StaffAccount domain) {
        if (domain == null) {
            return null;
        }
        return new UserAccountJpaEntity(
                domain.getUsername(),
                domain.getPasswordHash(),
                domain.getFullName(),
                domain.getArea() != null ? domain.getArea().name() : null,
                domain.isActive(),
                domain.getCreatedAt(),
                domain.getCreatedBy(),
                domain.getUpdatedAt(),
                domain.getUpdatedBy(),
                domain.getDeactivatedAt(),
                domain.getDeactivatedBy(),
                domain.getVersion());
    }
}
