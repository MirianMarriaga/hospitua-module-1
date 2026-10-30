package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.DailyReservationRoomJpaEntity;
import java.time.LocalDate;
import java.util.Collection;
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

    List<DailyReservationRoomJpaEntity> findByReservationRefInOrderByRoomNumber(Collection<String> reservationRefs);

    /** Habitaciones de las reservas del día sin una estancia para esa reserva y esa habitación (llegadas pendientes). */
    @Query("SELECT drr FROM DailyReservationRoomJpaEntity drr, DailyReservationJpaEntity dr"
            + " WHERE dr.reservationRef = drr.reservationRef AND dr.startDate = :operationalDate"
            + " AND NOT EXISTS (SELECT 1 FROM StayJpaEntity s"
            + " WHERE s.reservationRef = drr.reservationRef AND s.roomId = drr.roomId)")
    List<DailyReservationRoomJpaEntity> findPendingArrivalRooms(@Param("operationalDate") LocalDate operationalDate);

    /** Cuántas de {@link #findPendingArrivalRooms} existen en el inventario, como las filas de la pestaña Llegadas. */
    @Query("SELECT COUNT(drr) FROM DailyReservationRoomJpaEntity drr, DailyReservationJpaEntity dr"
            + " WHERE dr.reservationRef = drr.reservationRef AND dr.startDate = :operationalDate"
            + " AND NOT EXISTS (SELECT 1 FROM StayJpaEntity s"
            + " WHERE s.reservationRef = drr.reservationRef AND s.roomId = drr.roomId)"
            + " AND EXISTS (SELECT 1 FROM RoomJpaEntity r WHERE r.id = drr.roomId)")
    long countPendingArrivalRooms(@Param("operationalDate") LocalDate operationalDate);

    /**
     * Como {@link #findPendingArrivalRooms}, solo de las reservas cuya referencia o documento del titular es
     * {@code query}, o cuyo nombre, apellido o nombre completo contiene {@code pattern}; los dos van en minúsculas.
     */
    @Query("SELECT drr FROM DailyReservationRoomJpaEntity drr, DailyReservationJpaEntity dr"
            + " WHERE dr.reservationRef = drr.reservationRef AND dr.startDate = :operationalDate"
            + " AND NOT EXISTS (SELECT 1 FROM StayJpaEntity s"
            + " WHERE s.reservationRef = drr.reservationRef AND s.roomId = drr.roomId)"
            + " AND (LOWER(dr.reservationRef) = :query OR LOWER(dr.guestDocumentNumber) = :query"
            + " OR LOWER(dr.guestFirstName) LIKE :pattern ESCAPE '\\'"
            + " OR LOWER(dr.guestLastName) LIKE :pattern ESCAPE '\\'"
            + " OR LOWER(CONCAT(dr.guestFirstName, ' ', dr.guestLastName)) LIKE :pattern ESCAPE '\\')")
    List<DailyReservationRoomJpaEntity> searchPendingArrivalRooms(
            @Param("operationalDate") LocalDate operationalDate,
            @Param("query") String query,
            @Param("pattern") String pattern);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DailyReservationRoomJpaEntity r WHERE r.reservationRef = :reservationRef")
    void deleteByReservationRef(@Param("reservationRef") String reservationRef);
}
