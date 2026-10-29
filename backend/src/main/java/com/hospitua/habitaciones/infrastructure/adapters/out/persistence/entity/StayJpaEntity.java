package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla stay.
 */
@Entity
@Table(name = "stay")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StayJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "reservation_ref", nullable = false)
    private String reservationRef;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date")
    private LocalDate checkOutDate;

    @Column(name = "expected_checkin_date", nullable = false)
    private LocalDate expectedCheckinDate;

    @Column(name = "expected_checkout_date", nullable = false)
    private LocalDate expectedCheckoutDate;

    @Column(name = "titular_first_name", nullable = false)
    private String titularFirstName;

    @Column(name = "titular_last_name", nullable = false)
    private String titularLastName;

    @Column(name = "titular_document_number", nullable = false)
    private String titularDocumentNumber;

    @Version
    @Column(name = "version", nullable = false)
    private int version;
}
