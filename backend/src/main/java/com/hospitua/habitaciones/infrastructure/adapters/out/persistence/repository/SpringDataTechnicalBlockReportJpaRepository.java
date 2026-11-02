package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.domain.model.TechnicalBlockStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.TechnicalBlockReportJpaEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data JPA para la entidad TechnicalBlockReportJpaEntity. */
public interface SpringDataTechnicalBlockReportJpaRepository
        extends JpaRepository<TechnicalBlockReportJpaEntity, UUID> {

    /** Cruce de rangos con días inclusivos; usa {@code idx_technical_block_report_room_status}. */
    @Query("""
            SELECT r FROM TechnicalBlockReportJpaEntity r
            WHERE r.roomId = :roomId AND r.status IN :statuses
              AND r.technicalBlockStartDate <= :to AND r.estimatedTechnicalBlockEndDate >= :from
            ORDER BY r.technicalBlockStartDate
            """)
    List<TechnicalBlockReportJpaEntity> findOverlapping(
            @Param("roomId") UUID roomId,
            @Param("statuses") Collection<TechnicalBlockStatus> statuses,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Ordenados por habitación e inicio, para quedarse con el primero de cada una. */
    List<TechnicalBlockReportJpaEntity> findByRoomIdInAndStatusOrderByTechnicalBlockStartDateAsc(
            Collection<UUID> roomIds, TechnicalBlockStatus status);

    /** Usa el índice parcial {@code idx_technical_block_report_scheduled_start}. */
    @Query("""
            SELECT DISTINCT r.roomId FROM TechnicalBlockReportJpaEntity r
            WHERE r.status = :status
              AND r.technicalBlockStartDate <= :today AND r.estimatedTechnicalBlockEndDate >= :today
            """)
    List<UUID> findRoomIdsCovering(@Param("status") TechnicalBlockStatus status, @Param("today") LocalDate today);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TechnicalBlockReportJpaEntity r SET r.status = :expired
            WHERE r.status = :scheduled AND r.estimatedTechnicalBlockEndDate < :today
            """)
    int expireEndedBefore(
            @Param("scheduled") TechnicalBlockStatus scheduled,
            @Param("expired") TechnicalBlockStatus expired,
            @Param("today") LocalDate today);
}
