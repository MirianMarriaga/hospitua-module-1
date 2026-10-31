package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.DamageReport;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DamageReportJpaEntity;
import org.springframework.stereotype.Component;

/** Mapper entre la entidad de dominio DamageReport y la entidad JPA DamageReportJpaEntity. */
@Component
public class DamageReportMapper {

    public DamageReport toDomain(DamageReportJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new DamageReport(
                entity.getId(),
                entity.getRoomId(),
                entity.getAuthorId(),
                entity.getDamageDescription(),
                entity.getReportDateTime());
    }

    public DamageReportJpaEntity toEntity(DamageReport domain) {
        if (domain == null) {
            return null;
        }
        return new DamageReportJpaEntity(
                domain.getId(),
                domain.getRoomId(),
                domain.getAuthorId(),
                domain.getDamageDescription(),
                domain.getReportDateTime());
    }
}
