package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla cleaning_task (migración V3). Sin {@code version}: la tarea se escribe con la fila de
 * la habitación bloqueada (regla 13 del plan base).
 */
@Entity
@Table(name = "cleaning_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CleaningTaskJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "cleaning_staff_member_id", nullable = false, length = 100)
    private String cleaningStaffMemberId;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time")
    private OffsetDateTime endDateTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 50)
    private CleaningTaskOutcome outcome;
}
