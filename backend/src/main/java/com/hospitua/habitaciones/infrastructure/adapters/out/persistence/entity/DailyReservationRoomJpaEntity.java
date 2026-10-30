package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla daily_reservation_room, con PK compuesta (reservation_ref, room_id)
 * (plan-consultar-reservas.md T005).
 */
@Entity
@Table(name = "daily_reservation_room")
@IdClass(DailyReservationRoomJpaEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyReservationRoomJpaEntity {

    @Id
    @Column(name = "reservation_ref", length = 100, nullable = false)
    private String reservationRef;

    @Id
    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "room_number", length = 50, nullable = false)
    private String roomNumber;

    @Column(name = "category_room", length = 50, nullable = false)
    private String categoryRoom;

    @Column(name = "guest_count", nullable = false)
    private int guestCount;

    /** Clave compuesta de la habitación de una reserva. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String reservationRef;
        private UUID roomId;
    }
}
