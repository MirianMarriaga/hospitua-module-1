package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.domain.model.CleaningTaskOutcome;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.CleaningTaskJpaEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data JPA para la entidad CleaningTaskJpaEntity. */
public interface SpringDataCleaningTaskJpaRepository extends JpaRepository<CleaningTaskJpaEntity, UUID> {

    @Query("SELECT t.roomId FROM CleaningTaskJpaEntity t WHERE t.id = :taskId")
    Optional<UUID> findRoomIdById(@Param("taskId") UUID taskId);

    Optional<CleaningTaskJpaEntity> findByCleaningStaffMemberIdAndEndDateTimeIsNull(String cleaningStaffMemberId);

    /** Usa el índice parcial {@code idx_cleaning_task_last_cleaning} cuando los outcomes son los de la última limpieza. */
    @Query("""
            SELECT t.roomId AS roomId, MAX(t.endDateTime) AS lastCleaningEnd
            FROM CleaningTaskJpaEntity t
            WHERE t.outcome IN :outcomes AND t.roomId IN :roomIds
            GROUP BY t.roomId
            """)
    List<LastCleaningRow> findLastCleaningEnds(
            @Param("roomIds") Collection<UUID> roomIds, @Param("outcomes") Collection<CleaningTaskOutcome> outcomes);

    /** Fila de {@link #findLastCleaningEnds}. */
    interface LastCleaningRow {

        UUID getRoomId();

        OffsetDateTime getLastCleaningEnd();
    }
}
