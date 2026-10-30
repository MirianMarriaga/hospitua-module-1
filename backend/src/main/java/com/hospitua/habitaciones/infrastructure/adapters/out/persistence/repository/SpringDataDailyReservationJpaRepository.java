package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationJpaEntity;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para la entidad DailyReservationJpaEntity.
 */
public interface SpringDataDailyReservationJpaRepository extends JpaRepository<DailyReservationJpaEntity, String> {

    /** Llegada pendiente: habitación de una reserva del día sin una estancia para esa reserva y esa habitación. */
    @Query(
            value = "SELECT drr.reservation_ref FROM daily_reservation_room drr"
                    + " JOIN daily_reservation dr ON dr.reservation_ref = drr.reservation_ref"
                    + " WHERE drr.room_id = :roomId AND dr.start_date = :operationalDate"
                    + " AND NOT EXISTS (SELECT 1 FROM stay s"
                    + " WHERE s.reservation_ref = drr.reservation_ref AND s.room_id = drr.room_id)"
                    + " ORDER BY drr.reservation_ref LIMIT 1",
            nativeQuery = true)
    Optional<String> findPendingArrivalReservationRef(
            @Param("roomId") UUID roomId, @Param("operationalDate") LocalDate operationalDate);
}
