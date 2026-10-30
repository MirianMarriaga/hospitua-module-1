package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationRoomJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para la entidad DailyReservationRoomJpaEntity.
 */
public interface SpringDataDailyReservationRoomJpaRepository
        extends JpaRepository<DailyReservationRoomJpaEntity, DailyReservationRoomJpaEntity.Key> {

    List<DailyReservationRoomJpaEntity> findByReservationRefOrderByRoomNumber(String reservationRef);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DailyReservationRoomJpaEntity r WHERE r.reservationRef = :reservationRef")
    void deleteByReservationRef(@Param("reservationRef") String reservationRef);
}
