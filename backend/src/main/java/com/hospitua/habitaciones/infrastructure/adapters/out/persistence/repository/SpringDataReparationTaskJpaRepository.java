package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.ReparationTaskJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data JPA para la entidad ReparationTaskJpaEntity. */
public interface SpringDataReparationTaskJpaRepository extends JpaRepository<ReparationTaskJpaEntity, UUID> {

    @Query("SELECT t.roomId FROM ReparationTaskJpaEntity t WHERE t.id = :taskId")
    Optional<UUID> findRoomIdById(@Param("taskId") UUID taskId);

    Optional<ReparationTaskJpaEntity> findByMaintenanceStaffMemberIdAndEndDateTimeIsNull(
            String maintenanceStaffMemberId);

    boolean existsByRoomIdAndEndDateTimeIsNull(UUID roomId);

    /** Usa el índice único parcial {@code uk_reparation_task_open_by_room}: a lo sumo una fila por habitación. */
    @Query("SELECT t.roomId FROM ReparationTaskJpaEntity t WHERE t.endDateTime IS NULL")
    List<UUID> findRoomIdsWithOpenTask();
}
