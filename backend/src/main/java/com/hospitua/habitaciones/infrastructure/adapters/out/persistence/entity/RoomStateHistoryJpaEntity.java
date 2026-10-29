package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import com.hospitua.habitaciones.domain.model.RoomStatus;
import com.hospitua.habitaciones.domain.model.SourceFlow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla room_state_history.
 */
@Entity
@Table(name = "room_state_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateHistoryJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RoomStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private RoomStatus previousStatus;

    @Column(name = "start_date_time", nullable = false)
    private OffsetDateTime startDateTime;

    @Column(name = "end_date_time")
    private OffsetDateTime endDateTime;

    @Column(name = "actor_id")
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_flow", nullable = false)
    private SourceFlow sourceFlow;

    @Column(name = "reservation_ref")
    private String reservationRef;
}
