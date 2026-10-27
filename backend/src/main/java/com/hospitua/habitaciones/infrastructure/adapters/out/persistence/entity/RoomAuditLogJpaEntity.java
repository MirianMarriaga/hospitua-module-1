package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import com.hospitua.habitaciones.domain.model.RoomAuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidad JPA mapeada a la tabla room_audit_log.
 */
@Entity
@Table(name = "room_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomAuditLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private RoomAuditEventType eventType;

    @Column(name = "actor_id")
    private String actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "event_date_time", nullable = false)
    private OffsetDateTime eventDateTime;
}
