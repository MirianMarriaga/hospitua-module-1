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
 * Entidad JPA mapeada a la tabla daily_reservation_message_log (plan-consultar-reservas.md T005).
 */
@Entity
@Table(name = "daily_reservation_message_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyReservationMessageLogJpaEntity {

    @Id
    @Column(name = "message_id", length = 100, nullable = false)
    private String messageId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "operational_date", nullable = false)
    private LocalDate operationalDate;

    @Column(name = "message_type", length = 50, nullable = false)
    private String messageType;

    @Column(name = "reservation_ref", length = 100)
    private String reservationRef;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;
}
