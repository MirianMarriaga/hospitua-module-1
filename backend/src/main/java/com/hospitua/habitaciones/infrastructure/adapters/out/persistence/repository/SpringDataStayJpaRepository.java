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

    /**
     * Estancias activas con salida esperada en {@code date} o antes; cada fila es la estancia, el número y la categoría
     * de su habitación y la cantidad de huéspedes registrados.
     */
    @Query("SELECT s, r.roomNumber, r.categoryRoom,"
            + " (SELECT COUNT(g) FROM RoomGuestJpaEntity g WHERE g.stayId = s.id)"
            + " FROM StayJpaEntity s, RoomJpaEntity r"
            + " WHERE r.id = s.roomId AND s.checkOutDate IS NULL AND s.expectedCheckoutDate <= :date")
    List<Object[]> findActiveDepartures(@Param("date") LocalDate date);

    /**
     * Como {@link #findActiveDepartures}, solo las estancias cuya referencia, documento del titular o número de
     * habitación es {@code query}, o cuyo nombre, apellido o nombre completo contiene {@code pattern}; los dos van en
     * minúsculas.
     */
    @Query("SELECT s, r.roomNumber, r.categoryRoom,"
            + " (SELECT COUNT(g) FROM RoomGuestJpaEntity g WHERE g.stayId = s.id)"
            + " FROM StayJpaEntity s, RoomJpaEntity r"
            + " WHERE r.id = s.roomId AND s.checkOutDate IS NULL AND s.expectedCheckoutDate <= :date"
            + " AND (LOWER(s.reservationRef) = :query OR LOWER(s.titularDocumentNumber) = :query"
            + " OR LOWER(r.roomNumber) = :query"
            + " OR LOWER(s.titularFirstName) LIKE :pattern ESCAPE '\\'"
            + " OR LOWER(s.titularLastName) LIKE :pattern ESCAPE '\\'"
            + " OR LOWER(CONCAT(s.titularFirstName, ' ', s.titularLastName)) LIKE :pattern ESCAPE '\\')")
    List<Object[]> searchActiveDepartures(
            @Param("date") LocalDate date, @Param("query") String query, @Param("pattern") String pattern);
}
