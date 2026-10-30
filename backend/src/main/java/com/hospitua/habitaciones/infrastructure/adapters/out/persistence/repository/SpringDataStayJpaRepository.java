package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.repository;

import com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity.StayJpaEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio Spring Data JPA para la entidad StayJpaEntity.
 */
public interface SpringDataStayJpaRepository extends JpaRepository<StayJpaEntity, UUID> {

    /** Proyección común de las dos consultas de salidas: una {@link DepartureRow} por estancia. */
    String SELECT_DEPARTURE_ROWS = "SELECT new com.hospitua.habitaciones.infrastructure.adapters.out.persistence"
            + ".repository.DepartureRow(s, r.roomNumber, r.categoryRoom,"
            + " (SELECT COUNT(g) FROM RoomGuestJpaEntity g WHERE g.stayId = s.id))"
            + " FROM StayJpaEntity s, RoomJpaEntity r";

    /** Estancias activas con salida esperada en {@code date} o antes, con su habitación y sus huéspedes. */
    @Query(SELECT_DEPARTURE_ROWS
            + " WHERE r.id = s.roomId AND s.checkOutDate IS NULL AND s.expectedCheckoutDate <= :date")
    List<DepartureRow> findActiveDepartures(@Param("date") LocalDate date);

    long countByCheckOutDateIsNullAndExpectedCheckoutDate(LocalDate date);

    long countByCheckOutDateIsNullAndExpectedCheckoutDateBefore(LocalDate date);

    /**
     * Como {@link #findActiveDepartures}, solo las estancias cuya referencia, documento del titular o número de
     * habitación es {@code query}, o cuyo nombre, apellido o nombre completo contiene {@code pattern}; los dos van en
     * minúsculas.
     */
    @Query(SELECT_DEPARTURE_ROWS
            + " WHERE r.id = s.roomId AND s.checkOutDate IS NULL AND s.expectedCheckoutDate <= :date"
            + " AND (LOWER(s.reservationRef) = :query OR LOWER(s.titularDocumentNumber) = :query"
            + " OR LOWER(r.roomNumber) = :query"
            + " OR LOWER(s.titularFirstName) LIKE :pattern ESCAPE '\\'"
            + " OR LOWER(s.titularLastName) LIKE :pattern ESCAPE '\\'"
            + " OR LOWER(CONCAT(s.titularFirstName, ' ', s.titularLastName)) LIKE :pattern ESCAPE '\\')")
    List<DepartureRow> searchActiveDepartures(
            @Param("date") LocalDate date, @Param("query") String query, @Param("pattern") String pattern);
}
