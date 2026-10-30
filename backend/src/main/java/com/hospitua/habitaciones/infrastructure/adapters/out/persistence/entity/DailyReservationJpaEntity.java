package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla daily_reservation (plan-consultar-reservas.md T005).
 */
@Entity
@Table(name = "daily_reservation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyReservationJpaEntity {

    @Id
    @Column(name = "reservation_ref", length = 100, nullable = false)
    private String reservationRef;

    @Column(name = "guest_first_name", length = 100, nullable = false)
    private String guestFirstName;

    @Column(name = "guest_last_name", length = 100, nullable = false)
    private String guestLastName;

    @Column(name = "guest_document_type", length = 20, nullable = false)
    private String guestDocumentType;

    @Column(name = "guest_document_number", length = 50, nullable = false)
    private String guestDocumentNumber;

    @Column(name = "guest_nationality", length = 100, nullable = false)
    private String guestNationality;

    @Column(name = "source", length = 50, nullable = false)
    private String source;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
