package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomGuestJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad RoomGuestJpaEntity.
 */
public interface SpringDataRoomGuestJpaRepository extends JpaRepository<RoomGuestJpaEntity, UUID> {

    List<RoomGuestJpaEntity> findByStayId(UUID stayId);
}
