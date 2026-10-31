package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DamageReportJpaEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data JPA para la entidad DamageReportJpaEntity. */
public interface SpringDataDamageReportJpaRepository extends JpaRepository<DamageReportJpaEntity, UUID> {

    /** Usa el índice {@code idx_damage_report_room_date}. */
    Optional<DamageReportJpaEntity> findFirstByRoomIdOrderByReportDateTimeDesc(UUID roomId);

    /** Los reportes con la fecha más reciente de cada habitación; también usa {@code idx_damage_report_room_date}. */
    @Query("""
            SELECT d FROM DamageReportJpaEntity d
            WHERE d.roomId IN :roomIds
              AND d.reportDateTime = (
                  SELECT MAX(latest.reportDateTime) FROM DamageReportJpaEntity latest
                  WHERE latest.roomId = d.roomId)
            """)
    List<DamageReportJpaEntity> findLatestByRoomIds(@Param("roomIds") Collection<UUID> roomIds);
}
