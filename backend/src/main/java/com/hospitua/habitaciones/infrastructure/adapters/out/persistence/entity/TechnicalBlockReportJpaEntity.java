package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla technical_block_report (migración V3). Sin {@code version}: la aplicación y el cierre
 * ocurren con la fila de la habitación bloqueada, y la caducidad es un solo UPDATE (regla 13 del plan base).
 */
@Entity
@Table(name = "technical_block_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TechnicalBlockReportJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "maintenance_staff_member_id", nullable = false, length = 100)
    private String maintenanceStaffMemberId;

    @Column(name = "technical_block_reason", nullable = false, length = 500)
    private String technicalBlockReason;

    @Column(name = "technical_block_start_date", nullable = false)
    private LocalDate technicalBlockStartDate;

    @Column(name = "estimated_technical_block_end_date", nullable = false)
    private LocalDate estimatedTechnicalBlockEndDate;

    @Column(name = "report_date_time", nullable = false)
    private OffsetDateTime reportDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private TechnicalBlockStatus status;
}
