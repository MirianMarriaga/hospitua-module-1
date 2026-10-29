package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomAuditLogJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para la entidad RoomAuditLogJpaEntity.
 */
public interface SpringDataRoomAuditLogJpaRepository extends JpaRepository<RoomAuditLogJpaEntity, UUID> {}
