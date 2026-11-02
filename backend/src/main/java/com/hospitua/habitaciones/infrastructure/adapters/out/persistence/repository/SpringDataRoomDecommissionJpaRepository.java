package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomDecommissionJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio Spring Data JPA para la entidad RoomDecommissionJpaEntity. */
public interface SpringDataRoomDecommissionJpaRepository extends JpaRepository<RoomDecommissionJpaEntity, UUID> {

    /** La baja sin revertir más reciente; usa el índice {@code idx_room_decommission_room_date}. */
    Optional<RoomDecommissionJpaEntity> findFirstByRoomIdAndReactivationDateTimeIsNullOrderByDecommissionDateTimeDesc(
            UUID roomId);
}
