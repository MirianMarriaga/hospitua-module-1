package com.hospitua.habitaciones.application.ports.out;

import com.hospitua.habitaciones.domain.model.DamageReport;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistencia de los reportes de daño (plan-marcar-habitacion-inhabilitada-por-reparaciones.md T002). Solo inserta y
 * consulta: un reporte no se modifica ni se elimina (FR-010).
 */
public interface DamageReportRepositoryPort {

    DamageReport save(DamageReport report);

    /** El último reporte de la habitación: "Ver informe" y el aviso {@code CANCELLED} de la reparación finalizada. */
    Optional<DamageReport> findLatestByRoom(UUID roomId);

    /**
     * El último reporte de cada habitación, en una sola consulta, para el aviso diario de las 00:00; las habitaciones
     * sin reportes no aparecen.
     */
    Map<UUID, DamageReport> findLatestByRoomIds(Collection<UUID> roomIds);
}
