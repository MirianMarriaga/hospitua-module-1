package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.RoomJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para la entidad RoomJpaEntity.
 */
public interface SpringDataRoomJpaRepository
        extends JpaRepository<RoomJpaEntity, UUID>, JpaSpecificationExecutor<RoomJpaEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomJpaEntity r WHERE r.id = :id")
    Optional<RoomJpaEntity> findByIdWithPessimisticLock(@Param("id") UUID id);

    List<RoomJpaEntity> findByStatusIn(Collection<RoomStatus> statuses);

    List<RoomJpaEntity> findByStatusNot(RoomStatus status);

    Optional<RoomJpaEntity> findByRoomNumber(String roomNumber);

    boolean existsByRoomNumber(String roomNumber);
}
