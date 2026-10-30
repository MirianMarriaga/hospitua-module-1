package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationMessageLogJpaEntity;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para la entidad DailyReservationMessageLogJpaEntity.
 */
public interface SpringDataDailyReservationMessageLogJpaRepository
        extends JpaRepository<DailyReservationMessageLogJpaEntity, String> {

    @Query(
            "SELECT MAX(l.operationalDate) FROM DailyReservationMessageLogJpaEntity l WHERE l.messageType = :messageType")
    Optional<LocalDate> findLatestOperationalDate(@Param("messageType") String messageType);

    boolean existsByMessageTypeAndOperationalDate(String messageType, LocalDate operationalDate);

    @Query("SELECT MAX(l.sequenceNumber) FROM DailyReservationMessageLogJpaEntity l"
            + " WHERE l.operationalDate = :operationalDate")
    Optional<Long> findLastSequence(@Param("operationalDate") LocalDate operationalDate);

    @Query("SELECT MAX(l.sequenceNumber) FROM DailyReservationMessageLogJpaEntity l"
            + " WHERE l.operationalDate = :operationalDate AND l.reservationRef = :reservationRef")
    Optional<Long> findLastSequenceForReservation(
            @Param("operationalDate") LocalDate operationalDate, @Param("reservationRef") String reservationRef);
}
