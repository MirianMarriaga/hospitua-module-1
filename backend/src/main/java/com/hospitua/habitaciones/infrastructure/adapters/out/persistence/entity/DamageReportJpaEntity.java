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

/** Entidad JPA mapeada a la tabla damage_report (migración V3). Sin {@code version}: el reporte solo se inserta. */
@Entity
@Table(name = "damage_report")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DamageReportJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "author_id", nullable = false, length = 100)
    private String authorId;

    @Column(name = "damage_description", nullable = false, length = 500)
    private String damageDescription;

    @Column(name = "report_date_time", nullable = false)
    private OffsetDateTime reportDateTime;
}
