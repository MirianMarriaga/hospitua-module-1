package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla room_guest.
 */
@Entity
@Table(name = "room_guest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomGuestJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "document_type", nullable = false)
    private String documentType;

    @Column(name = "document_number", nullable = false)
    private String documentNumber;

    @Column(name = "nationality", nullable = false)
    private String nationality;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "origin_place")
    private String originPlace;

    @Column(name = "destination_place")
    private String destinationPlace;

    @Column(name = "is_reservation_guest", nullable = false)
    private boolean isReservationGuest;
}
