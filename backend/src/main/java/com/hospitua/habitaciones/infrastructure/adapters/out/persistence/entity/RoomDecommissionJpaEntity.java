package com.hospitua.habitaciones.infrastructure.adapters.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entidad JPA mapeada a la tabla room_decommission (migración V1). Sin {@code version}: la baja y su reactivación se
 * escriben con la fila de la habitación bloqueada.
 */
@Entity
@Table(name = "room_decommission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoomDecommissionJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "reason_category", nullable = false, length = 50)
    private String reasonCategory;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "decommissioned_by", length = 100)
    private String decommissionedBy;

    @Column(name = "decommission_date_time", nullable = false)
    private OffsetDateTime decommissionDateTime;

    @Column(name = "reactivated_by", length = 100)
    private String reactivatedBy;

    @Column(name = "reactivation_date_time")
    private OffsetDateTime reactivationDateTime;
}
