package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.mapper;

import com.hospitua.habitaciones.domain.model.TechnicalBlockReport;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.TechnicalBlockReportJpaEntity;
import org.springframework.stereotype.Component;

/** Mapper entre la entidad de dominio TechnicalBlockReport y la entidad JPA TechnicalBlockReportJpaEntity. */
@Component
public class TechnicalBlockReportMapper {

    public TechnicalBlockReport toDomain(TechnicalBlockReportJpaEntity entity) {
        return new TechnicalBlockReport(
                entity.getId(),
                entity.getRoomId(),
                entity.getMaintenanceStaffMemberId(),
                entity.getTechnicalBlockReason(),
                entity.getTechnicalBlockStartDate(),
                entity.getEstimatedTechnicalBlockEndDate(),
                entity.getReportDateTime(),
                entity.getStatus());
    }

    public TechnicalBlockReportJpaEntity toEntity(TechnicalBlockReport domain) {
        return new TechnicalBlockReportJpaEntity(
                domain.getId(),
                domain.getRoomId(),
                domain.getMaintenanceStaffMemberId(),
                domain.getReason(),
                domain.getStartDate(),
                domain.getEstimatedEndDate(),
                domain.getReportDateTime(),
                domain.getStatus());
    }
}
