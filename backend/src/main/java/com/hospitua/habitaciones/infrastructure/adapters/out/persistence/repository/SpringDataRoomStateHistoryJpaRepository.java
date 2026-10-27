package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomStateHistoryJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad RoomStateHistoryJpaEntity.
 */
public interface SpringDataRoomStateHistoryJpaRepository extends JpaRepository<RoomStateHistoryJpaEntity, UUID> {

    Optional<RoomStateHistoryJpaEntity> findByRoomIdAndEndDateTimeIsNull(UUID roomId);
}
